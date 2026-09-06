package com.custos.modules.backup.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.backup.dto.RetentionCleanupRequest;
import com.custos.modules.backup.dto.RetentionCleanupResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionCleanupService {

    private final AuditLogService auditLogService;

    public RetentionCleanupResult executeRetentionCleanup(
            RetentionCleanupRequest request,
            UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        if (request == null || request.getBackupDirectory() == null || request.getBackupDirectory().trim().isEmpty()) {
            throw new IllegalArgumentException("Backup directory cannot be empty");
        }

        Path dirPath = Paths.get(request.getBackupDirectory().trim()).toAbsolutePath().normalize();
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("Backup directory does not exist or is not a directory: " + dirPath);
        }

        int retentionDays = Math.max(0, request.getRetentionDays());
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        String pattern = (request.getFilePattern() != null && !request.getFilePattern().trim().isEmpty())
                ? request.getFilePattern().trim()
                : "*";

        PathMatcher matcher = dirPath.getFileSystem().getPathMatcher("glob:" + pattern);

        int scannedCount = 0;
        int deletedCount = 0;
        long freedBytes = 0;
        List<String> deletedFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    scannedCount++;
                    Path fileName = file.getFileName();
                    if (matcher.matches(fileName)) {
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        Instant fileTime = attrs.lastModifiedTime().toInstant();

                        if (fileTime.isBefore(cutoff)) {
                            long size = attrs.size();
                            try {
                                Files.delete(file);
                                deletedCount++;
                                freedBytes += size;
                                deletedFiles.add(fileName.toString());
                                log.info("Retention cleanup deleted expired backup file: {} ({} bytes, modified: {})",
                                        file, size, fileTime);
                            } catch (IOException e) {
                                log.warn("Failed to delete expired backup file {}: {}", file, e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan directory for retention cleanup: {}", e.getMessage(), e);
            throw new RuntimeException("Retention cleanup failed: " + e.getMessage(), e);
        }

        String details = String.format("Retention cleanup in %s (retention: %d days): deleted %d files, freed %d bytes",
                dirPath, retentionDays, deletedCount, freedBytes);

        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "RETENTION_CLEANUP",
                    "BACKUP_STORAGE:" + dirPath.getFileName(),
                    details,
                    servletRequest
            );
        } else {
            auditLogService.logAction(
                    null,
                    "SYSTEM",
                    "RETENTION_CLEANUP",
                    "BACKUP_STORAGE:" + dirPath.getFileName(),
                    "127.0.0.1",
                    details
            );
        }

        log.info(details);

        return RetentionCleanupResult.builder()
                .success(true)
                .directory(dirPath.toString())
                .retentionDays(retentionDays)
                .scannedFilesCount(scannedCount)
                .deletedFilesCount(deletedCount)
                .freedSpaceBytes(freedBytes)
                .deletedFileNames(deletedFiles)
                .executedAt(Instant.now())
                .message(deletedCount > 0 ? "Successfully purged " + deletedCount + " expired backup files" : "No expired files found matching retention policy")
                .build();
    }
}
