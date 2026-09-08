package com.custos.modules.backup.database;

import com.custos.modules.backup.dto.DatabaseBackupRequest;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.execution.ProcessSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class MySqlBackupEngine implements DatabaseBackupEngine {

    private static final int BUFFER_SIZE = 65536; // 64 KB streaming buffer
    private final ProcessSanitizer processSanitizer;

    @Override
    public List<String> buildCommandList(DatabaseBackupRequest request) {
        if (request == null || request.getDatabaseName() == null || request.getDatabaseName().trim().isEmpty()) {
            throw new IllegalArgumentException("Database name is required");
        }

        processSanitizer.validateIdentifier(request.getDatabaseName().trim(), "databaseName");

        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");

        if (request.getHost() != null && !request.getHost().trim().isEmpty()) {
            processSanitizer.validateHostname(request.getHost().trim());
            cmd.add("--host=" + request.getHost().trim());
        }

        if (request.getPort() != null && request.getPort() > 0) {
            cmd.add("--port=" + request.getPort());
        }

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            processSanitizer.validateArgument(request.getUsername().trim());
            cmd.add("--user=" + request.getUsername().trim());
        }

        cmd.add("--single-transaction");
        cmd.add("--quick");
        cmd.add("--skip-lock-tables");
        cmd.add("--add-drop-table");

        cmd.add(request.getDatabaseName().trim());

        if (request.getTables() != null && !request.getTables().isEmpty()) {
            for (String table : request.getTables()) {
                if (table != null && !table.trim().isEmpty()) {
                    processSanitizer.validateIdentifier(table.trim(), "tableName");
                    cmd.add(table.trim());
                }
            }
        }

        return cmd;
    }

    @Override
    public BackupResult executeBackup(DatabaseBackupRequest request, File targetFile) {
        List<String> cmd = buildCommandList(request);
        String executable = resolveExecutable(cmd.get(0));
        processSanitizer.validateExecutable(executable);
        cmd.set(0, executable);

        Map<String, String> env = new HashMap<>();
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            env.put("MYSQL_PWD", request.getPassword());
        }

        boolean isGzip = request.getCompressionFormat() == null || request.getCompressionFormat() == CompressionFormat.GZIP;
        Instant startTime = Instant.now();
        Process process = null;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (!env.isEmpty()) {
                pb.environment().putAll(env);
            }

            log.info("Starting MySQL dump for database: {} -> {}", request.getDatabaseName(), targetFile.getAbsolutePath());
            process = pb.start();

            // Background reader for stderr
            StringBuilder stderrBuffer = new StringBuilder();
            Process finalProcess = process;
            Thread errThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrBuffer.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            });
            errThread.setDaemon(true);
            errThread.start();

            // Zero-RAM Pipe: stdout -> [Gzip] -> Digest -> File
            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
            long uncompressedBytes = 0;

            try (
                    InputStream in = new BufferedInputStream(process.getInputStream(), BUFFER_SIZE);
                    OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(targetFile), BUFFER_SIZE);
                    DigestOutputStream digestOut = new DigestOutputStream(fileOut, sha256Digest);
                    OutputStream streamOut = isGzip ? new GZIPOutputStream(digestOut) : digestOut
            ) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    streamOut.write(buffer, 0, bytesRead);
                    uncompressedBytes += bytesRead;
                }
                streamOut.flush();
            }

            int timeoutSeconds = request.getTimeoutSeconds() > 0 ? request.getTimeoutSeconds() : 600;
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                deleteFileQuietly(targetFile);
                throw new RuntimeException("mysqldump timed out after " + timeoutSeconds + " seconds");
            }

            errThread.join(2000);
            int exitCode = process.exitValue();

            if (exitCode != 0) {
                deleteFileQuietly(targetFile);
                String errorMsg = stderrBuffer.toString().trim();
                log.error("mysqldump exited with error code {}: {}", exitCode, errorMsg);
                throw new RuntimeException("mysqldump failed (code " + exitCode + "): " + errorMsg);
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            long fileSizeBytes = targetFile.length();
            String checksumSha256 = HexFormat.of().formatHex(sha256Digest.digest());
            double compressionRatio = uncompressedBytes > 0 ? (double) fileSizeBytes / uncompressedBytes : 1.0;
            long tableCount = (request.getTables() != null && !request.getTables().isEmpty()) ? request.getTables().size() : 0;

            log.info("MySQL dump completed successfully: {} bytes, SHA-256: {} in {}ms",
                    fileSizeBytes, checksumSha256, durationMs);

            return BackupResult.builder()
                    .success(true)
                    .destinationPath(targetFile.getAbsolutePath())
                    .fileName(targetFile.getName())
                    .fileSizeBytes(fileSizeBytes)
                    .uncompressedSizeBytes(uncompressedBytes)
                    .checksumSha256(checksumSha256)
                    .durationMs(durationMs)
                    .itemCount(tableCount)
                    .compressionRatio(compressionRatio)
                    .createdAt(Instant.now())
                    .build();

        } catch (Exception e) {
            deleteFileQuietly(targetFile);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            log.error("MySQL dump execution error: {}", e.getMessage());
            throw new RuntimeException("MySQL backup failed: " + e.getMessage(), e);
        }
    }

    private String resolveExecutable(String executable) {
        if ("mysqldump".equalsIgnoreCase(executable)) {
            // Check if mariadb-dump is installed (e.g. Alpine Linux) to avoid deprecation warnings
            if (new File("/usr/bin/mariadb-dump").exists()) {
                return "mariadb-dump";
            }
        }
        return executable;
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Exception ignored) {}
        }
    }
}
