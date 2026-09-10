package com.custos.modules.drive.service;

import com.custos.modules.drive.config.GoogleCloudConfig;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Slf4j
@Service
public class GoogleDriveService {

    private final Drive googleDriveClient;
    private final GoogleCloudConfig googleCloudConfig;

    @Autowired
    public GoogleDriveService(
            @Autowired(required = false) Drive googleDriveClient,
            GoogleCloudConfig googleCloudConfig
    ) {
        this.googleDriveClient = googleDriveClient;
        this.googleCloudConfig = googleCloudConfig;
    }

    /**
     * Uploads a file stream directly to Google Drive (Zero-RAM streaming).
     */
    public File uploadFile(InputStream inputStream, long size, String fileName, String mimeType, String folderId) throws IOException {
        if (googleDriveClient == null) {
            throw new IllegalStateException("Google Drive client is not initialized. Please verify GCP credentials.");
        }

        String targetFolder = (folderId != null && !folderId.trim().isEmpty())
                ? folderId.trim()
                : googleCloudConfig.getDefaultFolderId();

        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        if (targetFolder != null && !targetFolder.trim().isEmpty()) {
            fileMetadata.setParents(Collections.singletonList(targetFolder));
        }

        InputStreamContent mediaContent = new InputStreamContent(mimeType != null ? mimeType : "application/octet-stream", inputStream);
        mediaContent.setLength(size);

        log.info("Streaming file '{}' ({} bytes) to Google Drive folder: {}", fileName, size, targetFolder);

        return googleDriveClient.files()
                .create(fileMetadata, mediaContent)
                .setFields("id, name, mimeType, size, webViewLink, webContentLink, parents")
                .execute();
    }

    /**
     * Deletes a file from Google Drive (used for compensating rollback transaction).
     */
    public boolean deleteFile(String fileId) {
        if (googleDriveClient == null) {
            log.warn("Cannot delete Google Drive file {}: client is null", fileId);
            return false;
        }

        try {
            log.warn("Executing rollback: deleting Google Drive file with ID: {}", fileId);
            googleDriveClient.files().delete(fileId).execute();
            log.info("Successfully deleted Google Drive file ID: {}", fileId);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete Google Drive file ID {} during rollback: {}", fileId, e.getMessage(), e);
            return false;
        }
    }
}
