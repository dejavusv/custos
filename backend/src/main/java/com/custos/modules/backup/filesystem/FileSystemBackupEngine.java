package com.custos.modules.backup.filesystem;

import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class FileSystemBackupEngine {

    private static final int BUFFER_SIZE = 65536; // 64 KB streaming buffer

    public BackupResult executeBackup(FileBackupRequest request, File targetFile) {
        if (request == null || request.getSourcePath() == null || request.getSourcePath().trim().isEmpty()) {
            throw new IllegalArgumentException("Source path cannot be empty");
        }

        Path sourcePath = Paths.get(request.getSourcePath().trim()).toAbsolutePath().normalize();
        if (!Files.exists(sourcePath)) {
            throw new IllegalArgumentException("Source path does not exist: " + sourcePath);
        }

        Instant startTime = Instant.now();
        List<PathMatcher> matchers = buildExclusionMatchers(request.getExclusionPatterns(), sourcePath.getFileSystem());

        CompressionFormat format = request.getCompressionFormat() != null ? request.getCompressionFormat() : CompressionFormat.TAR_GZ;

        try {
            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
            long[] stats; // [uncompressedBytes, fileCount]

            try (
                    OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile), BUFFER_SIZE);
                    DigestOutputStream digestOut = new DigestOutputStream(fileOut, sha256Digest)
            ) {
                if (format == CompressionFormat.ZIP) {
                    stats = archiveAsZip(sourcePath, digestOut, matchers);
                } else {
                    // Default to TAR_GZ
                    stats = archiveAsTarGz(sourcePath, digestOut, matchers);
                }
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            long fileSizeBytes = targetFile.length();
            long uncompressedBytes = stats[0];
            long fileCount = stats[1];
            String checksumSha256 = HexFormat.of().formatHex(sha256Digest.digest());
            double compressionRatio = uncompressedBytes > 0 ? (double) fileSizeBytes / uncompressedBytes : 1.0;

            log.info("File backup completed for {}: {} files, {} bytes (compressed: {} bytes, SHA-256: {}) in {}ms",
                    sourcePath, fileCount, uncompressedBytes, fileSizeBytes, checksumSha256, durationMs);

            return BackupResult.builder()
                    .success(true)
                    .destinationPath(targetFile.getAbsolutePath())
                    .fileName(targetFile.getName())
                    .fileSizeBytes(fileSizeBytes)
                    .uncompressedSizeBytes(uncompressedBytes)
                    .checksumSha256(checksumSha256)
                    .durationMs(durationMs)
                    .itemCount(fileCount)
                    .compressionRatio(compressionRatio)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            deleteFileQuietly(targetFile);
            log.error("File backup failed for source {}: {}", sourcePath, e.getMessage(), e);
            throw new RuntimeException("File backup failed: " + e.getMessage(), e);
        }
    }

    private long[] archiveAsTarGz(Path sourcePath, OutputStream out, List<PathMatcher> matchers) throws IOException {
        long uncompressedBytes = 0;
        long fileCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (
                GZIPOutputStream gzipOut = new GZIPOutputStream(out);
                TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut)
        ) {
            tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarOut.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

            Deque<Path> stack = new ArrayDeque<>();
            stack.push(sourcePath);

            while (!stack.isEmpty()) {
                Path current = stack.pop();
                if (isExcluded(current, sourcePath, matchers)) {
                    continue;
                }

                String relativeName = sourcePath.relativize(current).toString().replace('\\', '/');

                if (Files.isDirectory(current)) {
                    if (!current.equals(sourcePath)) {
                        TarArchiveEntry entry = new TarArchiveEntry(current.toFile(), relativeName + "/");
                        tarOut.putArchiveEntry(entry);
                        tarOut.closeArchiveEntry();
                    }

                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                        for (Path child : stream) {
                            stack.push(child);
                        }
                    }
                } else if (Files.isRegularFile(current)) {
                    TarArchiveEntry entry = new TarArchiveEntry(current.toFile(), relativeName.isEmpty() ? current.getFileName().toString() : relativeName);
                    tarOut.putArchiveEntry(entry);

                    try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(current), BUFFER_SIZE)) {
                        int read;
                        while ((read = fileIn.read(buffer)) != -1) {
                            tarOut.write(buffer, 0, read);
                            uncompressedBytes += read;
                        }
                    }
                    tarOut.closeArchiveEntry();
                    fileCount++;
                }
            }
            tarOut.finish();
        }

        return new long[]{uncompressedBytes, fileCount};
    }

    private long[] archiveAsZip(Path sourcePath, OutputStream out, List<PathMatcher> matchers) throws IOException {
        long uncompressedBytes = 0;
        long fileCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {
            Deque<Path> stack = new ArrayDeque<>();
            stack.push(sourcePath);

            while (!stack.isEmpty()) {
                Path current = stack.pop();
                if (isExcluded(current, sourcePath, matchers)) {
                    continue;
                }

                String relativeName = sourcePath.relativize(current).toString().replace('\\', '/');

                if (Files.isDirectory(current)) {
                    if (!current.equals(sourcePath)) {
                        ZipEntry entry = new ZipEntry(relativeName + "/");
                        zipOut.putNextEntry(entry);
                        zipOut.closeEntry();
                    }

                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                        for (Path child : stream) {
                            stack.push(child);
                        }
                    }
                } else if (Files.isRegularFile(current)) {
                    ZipEntry entry = new ZipEntry(relativeName.isEmpty() ? current.getFileName().toString() : relativeName);
                    zipOut.putNextEntry(entry);

                    try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(current), BUFFER_SIZE)) {
                        int read;
                        while ((read = fileIn.read(buffer)) != -1) {
                            zipOut.write(buffer, 0, read);
                            uncompressedBytes += read;
                        }
                    }
                    zipOut.closeEntry();
                    fileCount++;
                }
            }
        }

        return new long[]{uncompressedBytes, fileCount};
    }

    private List<PathMatcher> buildExclusionMatchers(List<String> patterns, FileSystem fileSystem) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathMatcher> matchers = new ArrayList<>();
        for (String pattern : patterns) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                String clean = pattern.trim().replace('\\', '/');
                try {
                    matchers.add(fileSystem.getPathMatcher("glob:" + clean));
                } catch (Exception e) {
                    log.warn("Invalid glob pattern '{}': {}", clean, e.getMessage());
                }
            }
        }
        return matchers;
    }

    private boolean isExcluded(Path path, Path root, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) {
            return false;
        }

        Path relative = root.relativize(path);
        String relativeStr = relative.toString().replace('\\', '/');
        Path normalizedRelative = Paths.get(relativeStr);
        String fileName = path.getFileName() != null ? path.getFileName().toString() : "";

        for (PathMatcher matcher : matchers) {
            if (matcher.matches(normalizedRelative) || matcher.matches(Paths.get(fileName))) {
                return true;
            }
        }
        return false;
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {}
        }
    }
}
