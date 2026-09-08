package com.custos.modules.backup.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.backup.database.MySqlBackupEngine;
import com.custos.modules.backup.database.PostgreSqlBackupEngine;
import com.custos.modules.backup.dto.DatabaseBackupRequest;
import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.filesystem.FileSystemBackupEngine;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.vault.dto.DecryptedSecretPayload;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import com.custos.modules.vault.repository.CredentialVaultRepository;
import com.custos.modules.vault.service.CredentialService;
import com.custos.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.custos.modules.backup.dto.StorageBrowseResponse;
import com.custos.modules.backup.dto.StorageItemDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final MySqlBackupEngine mySqlBackupEngine;
    private final PostgreSqlBackupEngine postgreSqlBackupEngine;
    private final FileSystemBackupEngine fileSystemBackupEngine;
    private final CredentialVaultRepository credentialVaultRepository;
    private final CredentialService credentialService;
    private final AuditLogService auditLogService;

    @Value("${custos.backup.default-directory:storage/backups}")
    private String defaultBackupDir;

    public BackupResult backupDatabase(DatabaseBackupRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        CredentialType resolvedType = CredentialType.DATABASE_POSTGRESQL;

        if (request.getCredentialId() != null) {
            CredentialVault vault = credentialVaultRepository.findById(request.getCredentialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found: " + request.getCredentialId()));

            resolvedType = vault.getCredentialType();
            request.setHost(vault.getHost());
            request.setPort(vault.getPort());
            request.setUsername(vault.getUsername());
            if (request.getDatabaseName() == null || request.getDatabaseName().trim().isEmpty()) {
                request.setDatabaseName(vault.getDatabaseName());
            }

            DecryptedSecretPayload secret = credentialService.getDecryptedSecret(vault.getId());
            if (secret != null) {
                request.setPassword(secret.getPassword());
            }
        }

        String targetDir = (request.getDestinationDir() != null && !request.getDestinationDir().trim().isEmpty())
                ? request.getDestinationDir().trim()
                : defaultBackupDir;

        File dir = new File(targetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String extension = request.getCompressionFormat() == CompressionFormat.NONE ? ".sql" : ".sql.gz";
        String fileName = (request.getCustomFileName() != null && !request.getCustomFileName().trim().isEmpty())
                ? request.getCustomFileName().trim()
                : String.format("%s_%s%s", request.getDatabaseName(), timestamp, extension);

        File targetFile = new File(dir, fileName);

        BackupResult result;
        if (resolvedType == CredentialType.DATABASE_MYSQL) {
            result = mySqlBackupEngine.executeBackup(request, targetFile);
        } else {
            result = postgreSqlBackupEngine.executeBackup(request, targetFile);
        }

        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "DATABASE_BACKUP",
                    "DB:" + request.getDatabaseName(),
                    String.format("Backup created: %s (%d bytes, checksum: %s)", fileName, result.getFileSizeBytes(), result.getChecksumSha256()),
                    servletRequest
            );
        }

        return result;
    }

    public BackupResult backupFileSystem(FileBackupRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        String targetDir = (request.getDestinationDir() != null && !request.getDestinationDir().trim().isEmpty())
                ? request.getDestinationDir().trim()
                : defaultBackupDir;

        File dir = new File(targetDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        Path sourcePath = Paths.get(request.getSourcePath());
        String folderName = sourcePath.getFileName() != null ? sourcePath.getFileName().toString() : "backup";
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String extension = request.getCompressionFormat() == CompressionFormat.ZIP ? ".zip" : ".tar.gz";

        String fileName = (request.getCustomFileName() != null && !request.getCustomFileName().trim().isEmpty())
                ? request.getCustomFileName().trim()
                : String.format("%s_%s%s", folderName, timestamp, extension);

        File targetFile = new File(dir, fileName);

        BackupResult result = fileSystemBackupEngine.executeBackup(request, targetFile);

        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "FILE_BACKUP",
                    "FILE:" + sourcePath,
                    String.format("Archived %s -> %s (%d files, %d bytes)", sourcePath, fileName, result.getItemCount(), result.getFileSizeBytes()),
                    servletRequest
            );
        }

        return result;
    }

    public StorageBrowseResponse browseStorage(String requestedPath) {
        String targetPathStr = (requestedPath != null && !requestedPath.trim().isEmpty())
                ? requestedPath.trim()
                : defaultBackupDir;

        if (targetPathStr.contains("..\\") || targetPathStr.contains("../") || targetPathStr.equals("..")) {
            throw new SecurityException("Path traversal (..) is not permitted");
        }

        Path targetPath = Paths.get(targetPathStr).normalize();
        File targetDir = targetPath.toFile();

        // If target is defaultBackupDir and doesn't exist, create it automatically
        if (!targetDir.exists() && targetPathStr.equals(defaultBackupDir)) {
            targetDir.mkdirs();
        }

        if (!targetDir.exists()) {
            throw new IllegalArgumentException("Path does not exist: " + targetPathStr);
        }

        if (!targetDir.isDirectory()) {
            if (targetDir.isFile() && targetDir.getParentFile() != null) {
                targetDir = targetDir.getParentFile();
            } else {
                throw new IllegalArgumentException("Path is not a directory: " + targetPathStr);
            }
        }

        List<StorageItemDto> items = new ArrayList<>();
        File[] files = targetDir.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isHidden()) {
                    continue;
                }

                String name = f.getName();
                boolean isDir = f.isDirectory();
                long size = isDir ? 0L : f.length();
                Instant lastMod = Instant.ofEpochMilli(f.lastModified());
                String ext = isDir ? null : extractExtension(name);

                String itemPath = f.getPath().replace('\\', '/');

                items.add(StorageItemDto.builder()
                        .name(name)
                        .path(itemPath)
                        .absolutePath(f.getAbsolutePath().replace('\\', '/'))
                        .isDirectory(isDir)
                        .sizeBytes(size)
                        .lastModified(lastMod)
                        .extension(ext)
                        .build());
            }

            items.sort((a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }

        File parentFile = targetDir.getParentFile();
        String parentPathStr = parentFile != null ? parentFile.getPath().replace('\\', '/') : null;
        boolean canGoUp = parentFile != null && parentFile.exists();

        return StorageBrowseResponse.builder()
                .currentPath(targetDir.getPath().replace('\\', '/'))
                .absolutePath(targetDir.getAbsolutePath().replace('\\', '/'))
                .defaultDirectory(defaultBackupDir.replace('\\', '/'))
                .parentPath(parentPathStr)
                .canGoUp(canGoUp)
                .items(items)
                .build();
    }

    public StorageItemDto createDirectory(String parentPath, String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Folder name cannot be empty");
        }

        String cleanName = folderName.trim();
        if (cleanName.contains("/") || cleanName.contains("\\") || cleanName.contains("..")) {
            throw new SecurityException("Invalid folder name: " + cleanName);
        }

        String parentPathStr = (parentPath != null && !parentPath.trim().isEmpty())
                ? parentPath.trim()
                : defaultBackupDir;

        if (parentPathStr.contains("..\\") || parentPathStr.contains("../") || parentPathStr.equals("..")) {
            throw new SecurityException("Path traversal (..) is not permitted");
        }

        File parent = new File(parentPathStr);
        if (!parent.exists()) {
            parent.mkdirs();
        }

        File newDir = new File(parent, cleanName);
        if (newDir.exists()) {
            throw new IllegalArgumentException("Directory already exists: " + cleanName);
        }

        boolean created = newDir.mkdirs();
        if (!created) {
            throw new RuntimeException("Failed to create directory: " + cleanName);
        }

        return StorageItemDto.builder()
                .name(cleanName)
                .path(newDir.getPath().replace('\\', '/'))
                .absolutePath(newDir.getAbsolutePath().replace('\\', '/'))
                .isDirectory(true)
                .sizeBytes(0L)
                .lastModified(Instant.now())
                .extension(null)
                .build();
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return null;
        if (fileName.endsWith(".tar.gz")) return "tar.gz";
        if (fileName.endsWith(".sql.gz")) return "sql.gz";
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0 && lastDot < fileName.length() - 1) ? fileName.substring(lastDot + 1) : null;
    }
}
