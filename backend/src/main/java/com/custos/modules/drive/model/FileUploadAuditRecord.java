package com.custos.modules.drive.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadAuditRecord {

    private String id;
    private String systemSource;
    private String driveFileId;
    private String fileName;
    private String fileExtension;
    private Long fileSize;
    private String mimeType;
    private String folderId;
    private String webViewLink;
    private String webContentLink;
    private String uploadedBy;
    private String status; // SUCCESS, FAILED
    private String errorMessage;
    private String createdAt;
    private String updatedAt;
}
