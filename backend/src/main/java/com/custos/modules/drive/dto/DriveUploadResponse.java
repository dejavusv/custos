package com.custos.modules.drive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveUploadResponse {

    private String driveFileId;
    private String fileName;
    private String fileExtension;
    private Long fileSize;
    private String mimeType;
    private String folderId;
    private String webViewLink;
    private String webContentLink;
    private String systemSource;
    private String uploadedBy;
    private String status;
    private Instant createdAt;
}
