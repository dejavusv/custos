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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
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
}
