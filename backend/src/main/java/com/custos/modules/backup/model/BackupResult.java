package com.custos.modules.backup.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupResult {

    private boolean success;
    private String destinationPath;
    private String fileName;
    private long fileSizeBytes;
    private long uncompressedSizeBytes;
    private String checksumSha256;
    private long durationMs;
    private long itemCount;
    private Double compressionRatio;
    private Instant createdAt;
    private String errorMessage;
}
