package com.custos.modules.docker.service;

import com.custos.modules.backup.dto.StorageBrowseResponse;
import com.custos.modules.backup.dto.StorageItemDto;
import com.custos.modules.docker.dto.DockerContainerDto;
import com.custos.modules.docker.dto.DockerContainersResponse;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only access to files inside Docker containers that share a network with the backend.
 * Only list / inspect / exec "ls" / archive (GET) calls are made against the Docker Engine.
 */
@Slf4j
@Service
public class DockerContainerService {

    private static final int EXEC_TIMEOUT_SECONDS = 30;
    private static final int MAX_LS_OUTPUT_BYTES = 8 * 1024 * 1024;
    private static final List<String> MONTHS =
            List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");

    // drwxr-xr-x  2 root  root  4096 Oct  3 12:00 name   (GNU coreutils and busybox)
    private static final Pattern LS_LINE = Pattern.compile(
            "^([-dlbcpsDt?])[rwxsStT-]{9}[.+@]?\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)(?:,\\s*\\d+)?\\s+"
                    + "([A-Za-z]{3})\\s+(\\d{1,2})\\s+(\\d{2}:\\d{2}|\\d{4})\\s+(.+)$");

    private final ObjectProvider<DockerClient> clientProvider;

    @Value("${custos.docker.networks:}")
    private String configuredNetworks;

    public DockerContainerService(ObjectProvider<DockerClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    // ------------------------------------------------------------------ containers

    public DockerContainersResponse listContainers() {
        DockerClient client = clientProvider.getIfAvailable();
        if (client == null) {
            return unavailable("Docker integration is disabled (custos.docker.enabled=false)");
        }
        try {
            List<DockerContainerDto> containers = findAllowedContainers(client).stream()
                    .map(this::toDto)
                    .toList();
            return DockerContainersResponse.builder().available(true).containers(containers).build();
        } catch (Exception e) {
            log.warn("Cannot list Docker containers: {}", e.getMessage());
            return unavailable("Cannot connect to Docker Engine (is /var/run/docker.sock mounted into the backend?)");
        }
    }

    /** Resolves a container name/id to its canonical name, and ensures it is inside the allowed scope. */
    public String requireAllowedContainer(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            throw new BadRequestException("Docker container is required");
        }
        DockerClient client = requireClient();
        String wanted = nameOrId.trim();
        try {
            for (Container c : findAllowedContainers(client)) {
                if (containerName(c).equals(wanted) || c.getId().equals(wanted)
                        || (wanted.length() >= 12 && c.getId().startsWith(wanted))) {
                    return containerName(c);
                }
            }
        } catch (DockerException e) {
            throw new BadRequestException("Cannot reach Docker Engine: " + e.getMessage());
        }
        throw new ResourceNotFoundException("Container not found or not in an allowed Docker network: " + wanted);
    }

    // ------------------------------------------------------------------ browse

    public StorageBrowseResponse browse(String containerNameOrId, String requestedPath) {
        String container = requireAllowedContainer(containerNameOrId);
        String path = normalizePath(requestedPath == null || requestedPath.isBlank() ? "/" : requestedPath);

        List<String> lines = listDirectoryLines(requireClient(), container, path);

        if (isFileListing(path, lines)) {
            // ls was pointed at a file: browse its parent folder instead
            return browse(container, parentOf(path));
        }
        List<StorageItemDto> items = parseItems(path, lines);

        boolean canGoUp = !"/".equals(path);
        return StorageBrowseResponse.builder()
                .currentPath(path)
                .absolutePath(path)
                .defaultDirectory("/")
                .parentPath(canGoUp ? parentOf(path) : null)
                .canGoUp(canGoUp)
                .items(items)
                .build();
    }

    // ------------------------------------------------------------------ archive

    /** Opens a tar stream of the given path (file or directory) from the container. Caller must close it. */
    public InputStream openArchive(String containerNameOrId, String path) {
        String container = requireAllowedContainer(containerNameOrId);
        String normalized = normalizePath(path);
        try {
            return requireClient().copyArchiveFromContainerCmd(container, normalized).exec();
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            throw new ResourceNotFoundException("Path does not exist in container " + container + ": " + normalized);
        } catch (DockerException e) {
            throw new BadRequestException("Cannot read " + normalized + " from container " + container + ": " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ path helpers

    /** Validates and normalises a POSIX container path ("/a//b/" becomes "/a/b"). */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new BadRequestException("Container path is required");
        }
        String p = path.trim();
        if (!p.startsWith("/")) {
            throw new BadRequestException("Container path must be absolute (start with /)");
        }
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c < 0x20 || c == 0x7f) {
                throw new BadRequestException("Container path contains invalid characters");
            }
        }
        List<String> segments = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) {
                throw new BadRequestException("Path traversal (..) is not permitted");
            }
            segments.add(seg);
        }
        return segments.isEmpty() ? "/" : "/" + String.join("/", segments);
    }

    public static String parentOf(String normalizedPath) {
        int idx = normalizedPath.lastIndexOf('/');
        return idx <= 0 ? "/" : normalizedPath.substring(0, idx);
    }

    // ------------------------------------------------------------------ internals

    private DockerClient requireClient() {
        DockerClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new BadRequestException("Docker integration is disabled (custos.docker.enabled=false)");
        }
        return client;
    }

    private DockerContainersResponse unavailable(String message) {
        return DockerContainersResponse.builder().available(false).message(message).containers(List.of()).build();
    }

    private List<Container> findAllowedContainers(DockerClient client) {
        List<Container> running = client.listContainersCmd().withStatusFilter(List.of("running")).exec();

        Set<String> configured = parseNetworks(configuredNetworks);
        String selfId = null;
        Set<String> selfNetworks = Set.of();
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            try {
                InspectContainerResponse self = client.inspectContainerCmd(hostname).exec();
                selfId = self.getId();
                if (self.getNetworkSettings() != null && self.getNetworkSettings().getNetworks() != null) {
                    selfNetworks = self.getNetworkSettings().getNetworks().keySet();
                }
            } catch (Exception e) {
                log.debug("Backend does not appear to run inside a container: {}", e.getMessage());
            }
        }

        Set<String> allowedNetworks = !configured.isEmpty() ? configured : (selfNetworks.isEmpty() ? null : selfNetworks);
        if (allowedNetworks == null) {
            log.warn("Cannot determine the backend's Docker network and custos.docker.networks is empty; "
                    + "listing all running containers");
        }

        List<Container> result = new ArrayList<>();
        for (Container c : running) {
            if (c.getId().equals(selfId)) continue;
            if (allowedNetworks == null || !Collections.disjoint(containerNetworks(c), allowedNetworks)) {
                result.add(c);
            }
        }
        result.sort((a, b) -> containerName(a).compareToIgnoreCase(containerName(b)));
        return result;
    }

    private static Set<String> containerNetworks(Container c) {
        if (c.getNetworkSettings() == null) return Set.of();
        Map<String, ContainerNetwork> networks = c.getNetworkSettings().getNetworks();
        return networks == null ? Set.of() : networks.keySet();
    }

    private static Set<String> parseNetworks(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv != null) {
            Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(out::add);
        }
        return out;
    }

    private static String containerName(Container c) {
        String[] names = c.getNames();
        if (names == null || names.length == 0) return c.getId().substring(0, Math.min(12, c.getId().length()));
        return names[0].startsWith("/") ? names[0].substring(1) : names[0];
    }

    private DockerContainerDto toDto(Container c) {
        return DockerContainerDto.builder()
                .name(containerName(c))
                .image(c.getImage())
                .status(c.getStatus())
                .networks(new ArrayList<>(new TreeSet<>(containerNetworks(c))))
                .build();
    }

    /** Runs `ls -lAp -- <path>` in the container (argv only, no shell) and returns its stdout lines. */
    private List<String> listDirectoryLines(DockerClient client, String container, String path) {
        BoundedOutputStream stdout = new BoundedOutputStream(MAX_LS_OUTPUT_BYTES);
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try {
            ExecCreateCmdResponse exec = client.execCreateCmd(container)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd("ls", "-lAp", "--", path)
                    .exec();

            boolean finished = client.execStartCmd(exec.getId())
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                throw new BadRequestException("Listing " + path + " in container " + container + " timed out");
            }

            Long exitCode = client.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
            String err = stderr.toString(StandardCharsets.UTF_8).trim();
            if (exitCode != null && exitCode != 0) {
                if (err.contains("No such file or directory")) {
                    throw new ResourceNotFoundException("Path does not exist in container: " + path);
                }
                throw new BadRequestException("Cannot list " + path + " in container " + container
                        + (err.isEmpty() ? " (exit code " + exitCode + ")" : ": " + abbreviate(err)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Listing interrupted");
        } catch (DockerException e) {
            throw new BadRequestException("Cannot list " + path + " in container " + container
                    + " (the container may have no 'ls' command): " + abbreviate(e.getMessage()));
        }
        return Arrays.asList(stdout.toString(StandardCharsets.UTF_8).split("\\r?\\n"));
    }

    /** True when `ls -l <path>` printed the path itself as a non-directory, i.e. the path is a file. */
    static boolean isFileListing(String path, List<String> lines) {
        if ("/".equals(path)) return false;
        for (String line : lines) {
            Matcher m = LS_LINE.matcher(line);
            if (m.matches() && m.group(1).charAt(0) != 'd' && m.group(6).equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Converts `ls -lAp` output for a directory into browse items (directories first, then by name). */
    static List<StorageItemDto> parseItems(String path, List<String> lines) {
        List<StorageItemDto> items = new ArrayList<>();
        for (String line : lines) {
            Matcher m = LS_LINE.matcher(line);
            if (!m.matches()) {
                continue; // "total N" and anything unrecognised
            }
            char type = m.group(1).charAt(0);
            boolean isDir = type == 'd';
            String name = m.group(6);
            if (type == 'l') {
                int arrow = name.indexOf(" -> ");
                if (arrow > 0) name = name.substring(0, arrow);
            }
            if (isDir && name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            if (name.isEmpty()) continue;

            String itemPath = "/".equals(path) ? "/" + name : path + "/" + name;
            items.add(StorageItemDto.builder()
                    .name(name)
                    .path(itemPath)
                    .absolutePath(itemPath)
                    .isDirectory(isDir)
                    .sizeBytes(isDir ? 0L : Long.parseLong(m.group(2)))
                    .lastModified(parseTimestamp(m.group(3), m.group(4), m.group(5)))
                    .extension(isDir ? null : extractExtension(name))
                    .build());
        }

        items.sort((a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });
        return items;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private static String extractExtension(String fileName) {
        if (fileName.endsWith(".tar.gz")) return "tar.gz";
        if (fileName.endsWith(".sql.gz")) return "sql.gz";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0 && lastDot < fileName.length() - 1) ? fileName.substring(lastDot + 1) : null;
    }

    /** Parses the date columns of `ls -l`. Containers normally run in UTC. */
    static Instant parseTimestamp(String mon, String day, String timeOrYear) {
        try {
            int month = MONTHS.indexOf(mon.substring(0, 1).toUpperCase() + mon.substring(1).toLowerCase()) + 1;
            if (month == 0) return Instant.EPOCH;
            int d = Integer.parseInt(day);
            if (timeOrYear.contains(":")) {
                LocalTime t = LocalTime.parse(timeOrYear);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                LocalDateTime candidate = LocalDateTime.of(now.getYear(), month, d, t.getHour(), t.getMinute());
                if (candidate.isAfter(now.plusDays(1))) candidate = candidate.minusYears(1);
                return candidate.toInstant(ZoneOffset.UTC);
            }
            return LocalDateTime.of(Integer.parseInt(timeOrYear), month, d, 0, 0).toInstant(ZoneOffset.UTC);
        } catch (DateTimeException | NumberFormatException e) {
            return Instant.EPOCH;
        }
    }

    /** Collects up to {@code limit} bytes, silently dropping the rest. */
    private static final class BoundedOutputStream extends ByteArrayOutputStream {
        private final int limit;

        BoundedOutputStream(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            int room = limit - size();
            if (room > 0) super.write(b, off, Math.min(len, room));
        }

        @Override
        public synchronized void write(int b) {
            if (size() < limit) super.write(b);
        }

        @Override
        public void close() throws IOException {
            // nothing to release
        }
    }
}
