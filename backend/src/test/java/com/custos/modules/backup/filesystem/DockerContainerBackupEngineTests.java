package com.custos.modules.backup.filesystem;

import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.docker.service.DockerContainerService;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DockerContainerBackupEngineTests {

    @TempDir
    File tempDir;

    private DockerContainerService dockerService;
    private DockerContainerBackupEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        dockerService = mock(DockerContainerService.class);
        when(dockerService.requireAllowedContainer("web")).thenReturn("web");
        when(dockerService.requireAllowedContainer("stranger"))
                .thenThrow(new ResourceNotFoundException("not in an allowed network"));
        when(dockerService.openArchive(eq("web"), eq("/usr/share/nginx/html"))).thenAnswer(inv -> htmlTar());
        engine = new DockerContainerBackupEngine(dockerService);
    }

    /** Same layout Docker's archive API returns: every entry is prefixed with the requested folder's base name. */
    private static InputStream htmlTar() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (TarArchiveOutputStream tar = new TarArchiveOutputStream(bytes)) {
            dir(tar, "html/");
            file(tar, "html/index.html", "<h1>hi</h1>");
            file(tar, "html/error.log", "boom");
            dir(tar, "html/assets/");
            file(tar, "html/assets/app.js", "console.log(1)");
            dir(tar, "html/node_modules/");
            file(tar, "html/node_modules/dep.js", "dep");
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static void dir(TarArchiveOutputStream tar, String name) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name);
        tar.putArchiveEntry(e);
        tar.closeArchiveEntry();
    }

    private static void file(TarArchiveOutputStream tar, String name, String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        TarArchiveEntry e = new TarArchiveEntry(name);
        e.setSize(data.length);
        tar.putArchiveEntry(e);
        tar.write(data);
        tar.closeArchiveEntry();
    }

    private FileBackupRequest request(CompressionFormat format, List<String> exclusions) {
        return FileBackupRequest.builder()
                .dockerContainer("web")
                .sourcePath("/usr/share/nginx/html")
                .compressionFormat(format)
                .exclusionPatterns(exclusions)
                .build();
    }

    @Test
    void tarGz_stripsRootPrefixAndAppliesExclusions() throws Exception {
        File target = new File(tempDir, "out.tar.gz");

        BackupResult result = engine.executeBackup(request(CompressionFormat.TAR_GZ, List.of("*.log", "node_modules")), target);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getItemCount(), "index.html and assets/app.js");

        Map<String, String> entries = new java.util.TreeMap<>();
        try (TarArchiveInputStream in = new TarArchiveInputStream(new GZIPInputStream(Files.newInputStream(target.toPath())))) {
            TarArchiveEntry e;
            while ((e = in.getNextEntry()) != null) {
                entries.put(e.getName(), e.isDirectory() ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertEquals(List.of("assets/", "assets/app.js", "index.html"), new ArrayList<>(entries.keySet()));
        assertEquals("<h1>hi</h1>", entries.get("index.html"));
    }

    @Test
    void tarGz_checksumMatchesWrittenFile() throws Exception {
        File target = new File(tempDir, "out.tar.gz");

        BackupResult result = engine.executeBackup(request(CompressionFormat.TAR_GZ, null), target);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target.toPath())));
        assertEquals(expected, result.getChecksumSha256());
        assertEquals(target.length(), result.getFileSizeBytes());
        assertEquals(4, result.getItemCount());
    }

    @Test
    void zip_containsFilteredEntries() throws Exception {
        File target = new File(tempDir, "out.zip");

        BackupResult result = engine.executeBackup(request(CompressionFormat.ZIP, List.of("*.log", "node_modules/**")), target);

        assertEquals(2, result.getItemCount());
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(target.toPath()))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        assertTrue(names.contains("index.html"));
        assertTrue(names.contains("assets/app.js"));
        // like the local engine, "node_modules/**" drops the contents but keeps the (empty) folder entry itself
        assertFalse(names.contains("node_modules/dep.js"), names.toString());
        assertFalse(names.stream().anyMatch(n -> n.endsWith(".log")), names.toString());
    }

    @Test
    void rootPathIsRejected() {
        FileBackupRequest req = FileBackupRequest.builder().dockerContainer("web").sourcePath("/").build();

        assertThrows(BadRequestException.class, () -> engine.executeBackup(req, new File(tempDir, "x.tar.gz")));
    }

    @Test
    void containerOutsideAllowedScopeIsRejectedAndNoFileLeft() {
        FileBackupRequest req = FileBackupRequest.builder().dockerContainer("stranger").sourcePath("/etc").build();
        File target = new File(tempDir, "x.tar.gz");

        assertThrows(ResourceNotFoundException.class, () -> engine.executeBackup(req, target));
        assertFalse(target.exists());
    }

    @Test
    void failureWhileReadingArchiveDeletesPartialFile() throws Exception {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };
        when(dockerService.openArchive(anyString(), anyString())).thenReturn(failing);
        File target = new File(tempDir, "broken.tar.gz");

        assertThrows(RuntimeException.class, () -> engine.executeBackup(request(CompressionFormat.TAR_GZ, null), target));
        assertFalse(target.exists());
    }
}
