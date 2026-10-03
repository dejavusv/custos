package com.custos.modules.backup.filesystem;

import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.docker.service.DockerContainerService;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Archives a file/folder that lives inside a Docker container. The container's tar stream (Docker archive API)
 * is re-packed into tar.gz / zip on the Custos server, applying the same exclusion globs as the local engine.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DockerContainerBackupEngine {

    private static final int BUFFER_SIZE = 65536;

    private final DockerContainerService dockerContainerService;

    public BackupResult executeBackup(FileBackupRequest request, File targetFile) {
        String sourcePath = DockerContainerService.normalizePath(request.getSourcePath());
        if ("/".equals(sourcePath)) {
            throw new BadRequestException("Select a folder or file inside the container, not the root (/)");
        }
        String container = dockerContainerService.requireAllowedContainer(request.getDockerContainer());

        Instant startTime = Instant.now();
        ExclusionMatcher matchers = ExclusionMatcher.of(request.getExclusionPatterns(), FileSystems.getDefault());
        CompressionFormat format = request.getCompressionFormat() != null ? request.getCompressionFormat() : CompressionFormat.TAR_GZ;

        try (InputStream raw = dockerContainerService.openArchive(container, sourcePath)) {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long[] stats;

            try (
                    TarArchiveInputStream tarIn = new TarArchiveInputStream(new BufferedInputStream(raw, BUFFER_SIZE));
                    OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile), BUFFER_SIZE);
                    DigestOutputStream digestOut = new DigestOutputStream(fileOut, sha256)
            ) {
                stats = format == CompressionFormat.ZIP
                        ? repackAsZip(tarIn, digestOut, matchers)
                        : repackAsTarGz(tarIn, digestOut, matchers);
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            long fileSizeBytes = targetFile.length();
            String checksum = HexFormat.of().formatHex(sha256.digest());
            double ratio = stats[0] > 0 ? (double) fileSizeBytes / stats[0] : 1.0;

            log.info("Docker file backup completed for {}:{} - {} files, {} bytes (compressed: {} bytes, SHA-256: {}) in {}ms",
                    container, sourcePath, stats[1], stats[0], fileSizeBytes, checksum, durationMs);

            return BackupResult.builder()
                    .success(true)
                    .destinationPath(targetFile.getAbsolutePath())
                    .fileName(targetFile.getName())
                    .fileSizeBytes(fileSizeBytes)
                    .uncompressedSizeBytes(stats[0])
                    .checksumSha256(checksum)
                    .durationMs(durationMs)
                    .itemCount(stats[1])
                    .compressionRatio(ratio)
                    .createdAt(Instant.now())
                    .build();
        } catch (BadRequestException | ResourceNotFoundException e) {
            deleteFileQuietly(targetFile);
            throw e;
        } catch (Exception e) {
            deleteFileQuietly(targetFile);
            log.error("Docker file backup failed for {}:{}: {}", container, sourcePath, e.getMessage(), e);
            throw new RuntimeException("Docker file backup failed: " + e.getMessage(), e);
        }
    }

    private long[] repackAsTarGz(TarArchiveInputStream tarIn, OutputStream out, ExclusionMatcher matchers) throws IOException {
        long uncompressedBytes = 0;
        long fileCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        String rootName = null;

        try (
                GZIPOutputStream gzipOut = new GZIPOutputStream(out);
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)
        ) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            TarArchiveEntry in;
            while ((in = tarIn.getNextEntry()) != null) {
                String name = trimSlashes(in.getName());
                if (rootName == null) {
                    rootName = firstSegment(name);
                }
                String relative = relativeToRoot(name, rootName, in.isDirectory());
                if (relative == null || matchers.isRelativePathExcluded(relative)) {
                    continue;
                }

                boolean regular = in.isFile();
                if (!regular && !in.isDirectory() && !in.isSymbolicLink() && !in.isLink()) {
                    continue; // devices, fifos, sockets
                }

                TarArchiveEntry outEntry = new TarArchiveEntry(in.isDirectory() ? relative + "/" : relative, in.getLinkFlag());
                outEntry.setMode(in.getMode());
                outEntry.setModTime(in.getModTime());
                outEntry.setUserId(in.getLongUserId());
                outEntry.setGroupId(in.getLongGroupId());
                outEntry.setUserName(in.getUserName());
                outEntry.setGroupName(in.getGroupName());
                if (in.isSymbolicLink() || in.isLink()) {
                    outEntry.setLinkName(in.getLinkName());
                }
                if (regular) {
                    outEntry.setSize(in.getSize());
                }

                tarOut.putArchiveEntry(outEntry);
                if (regular) {
                    int read;
                    while ((read = tarIn.read(buffer)) != -1) {
                        tarOut.write(buffer, 0, read);
                        uncompressedBytes += read;
                    }
                    fileCount++;
                }
                tarOut.closeArchiveEntry();
            }
            tarOut.finish();
        }
        return new long[]{uncompressedBytes, fileCount};
    }

    private long[] repackAsZip(TarArchiveInputStream tarIn, OutputStream out, ExclusionMatcher matchers) throws IOException {
        long uncompressedBytes = 0;
        long fileCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        String rootName = null;

        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            TarArchiveEntry in;
            while ((in = tarIn.getNextEntry()) != null) {
                String name = trimSlashes(in.getName());
                if (rootName == null) {
                    rootName = firstSegment(name);
                }
                String relative = relativeToRoot(name, rootName, in.isDirectory());
                if (relative == null || matchers.isRelativePathExcluded(relative)) {
                    continue;
                }
                if (in.isDirectory()) {
                    ZipEntry dir = new ZipEntry(relative + "/");
                    dir.setTime(in.getModTime().getTime());
                    zipOut.putNextEntry(dir);
                    zipOut.closeEntry();
                } else if (in.isFile()) {
                    ZipEntry file = new ZipEntry(relative);
                    file.setTime(in.getModTime().getTime());
                    zipOut.putNextEntry(file);
                    int read;
                    while ((read = tarIn.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, read);
                        uncompressedBytes += read;
                    }
                    zipOut.closeEntry();
                    fileCount++;
                } // symlinks, devices etc. cannot be represented in a zip and are skipped
            }
        }
        return new long[]{uncompressedBytes, fileCount};
    }

    /**
     * Docker prefixes every entry with the requested path's base name ("html/", "html/index.html").
     * Returns the name relative to that root, or null for the root directory entry itself.
     * When a single file was requested the entry is just the file name, which is kept.
     */
    private static String relativeToRoot(String name, String rootName, boolean isDirectory) {
        if (name.isEmpty()) return null;
        if (name.equals(rootName)) {
            return isDirectory ? null : name;
        }
        if (name.startsWith(rootName + "/")) {
            return name.substring(rootName.length() + 1);
        }
        return name;
    }

    private static String trimSlashes(String name) {
        String n = name;
        while (n.startsWith("./")) n = n.substring(2);
        while (n.startsWith("/")) n = n.substring(1);
        while (n.endsWith("/")) n = n.substring(0, n.length() - 1);
        return n;
    }

    private static String firstSegment(String name) {
        int idx = name.indexOf('/');
        return idx < 0 ? name : name.substring(0, idx);
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {
            }
        }
    }
}
