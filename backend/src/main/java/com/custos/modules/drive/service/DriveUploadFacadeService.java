package com.custos.modules.drive.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.drive.dto.DriveUploadResponse;
import com.custos.modules.drive.model.FileUploadAuditRecord;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriveUploadFacadeService {

    private final GoogleDriveService googleDriveService;
    private final FirestoreAuditService firestoreAuditService;

    /**
     * Handles end-to-end upload flow:
     * 1. Validate file & metadata
     * 2. Stream to Google Drive
     * 3. Record audit entry in Firestore
     * 4. If Firestore fails, delete file from Google Drive (Rollback)
     */
    public DriveUploadResponse uploadAndRecordAudit(
            MultipartFile file,
            String systemSource,
            String folderId,
            UserPrincipal currentUser
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be null or empty");
        }

        if (systemSource == null || systemSource.trim().isEmpty()) {
            throw new IllegalArgumentException("systemSource is required");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed_file";
        String extension = getFileExtension(originalFilename);
        long fileSize = file.getSize();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String username = currentUser != null ? currentUser.getUsername() : "anonymous";

        log.info("Starting Google Drive upload for file '{}' ({} bytes) from system '{}' by user '{}'",
                originalFilename, fileSize, systemSource, username);

        // Step 1: Upload to Google Drive via streaming
        File uploadedDriveFile;
        try (InputStream inputStream = file.getInputStream()) {
            uploadedDriveFile = googleDriveService.uploadFile(
                    inputStream,
                    fileSize,
                    originalFilename,
                    contentType,
                    folderId
            );
        } catch (Exception e) {
            log.error("Failed to upload file to Google Drive: {}", e.getMessage(), e);
            throw new IOException("Failed to upload file to Google Drive: " + e.getMessage(), e);
        }

        String driveFileId = uploadedDriveFile.getId();
        String webViewLink = uploadedDriveFile.getWebViewLink();
        String webContentLink = uploadedDriveFile.getWebContentLink();
        String resolvedFolderId = (uploadedDriveFile.getParents() != null && !uploadedDriveFile.getParents().isEmpty())
                ? uploadedDriveFile.getParents().get(0)
                : folderId;

        // Step 2: Record to Firebase Firestore with Rollback guarantee
        FileUploadAuditRecord auditRecord = FileUploadAuditRecord.builder()
                .systemSource(systemSource.trim())
                .driveFileId(driveFileId)
                .fileName(originalFilename)
                .fileExtension(extension)
                .fileSize(fileSize)
                .mimeType(contentType)
                .folderId(resolvedFolderId)
                .webViewLink(webViewLink)
                .webContentLink(webContentLink)
                .uploadedBy(username)
                .status("SUCCESS")
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();

        try {
            firestoreAuditService.recordUploadLog(auditRecord);
            log.info("Successfully recorded upload audit log to Firestore for file ID: {}", driveFileId);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to write audit log to Firestore. Rolling back Google Drive upload for file ID: {}", driveFileId, e);
            boolean deleted = googleDriveService.deleteFile(driveFileId);
            if (deleted) {
                log.info("Rollback successful: file {} deleted from Google Drive", driveFileId);
            } else {
                log.warn("Rollback warning: could not verify deletion of file {} from Google Drive", driveFileId);
            }
            throw new RuntimeException("Upload failed at Firestore audit stage: " + e.getMessage() + ". File was removed from Google Drive.", e);
        }

        return DriveUploadResponse.builder()
                .driveFileId(driveFileId)
                .fileName(originalFilename)
                .fileExtension(extension)
                .fileSize(fileSize)
                .mimeType(contentType)
                .folderId(resolvedFolderId)
                .webViewLink(webViewLink)
                .webContentLink(webContentLink)
                .systemSource(systemSource.trim())
                .uploadedBy(username)
                .status("SUCCESS")
                .createdAt(Instant.now())
                .build();
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }
}
