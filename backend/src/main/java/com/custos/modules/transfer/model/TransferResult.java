package com.custos.modules.transfer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferResult {

    private boolean success;
    private String targetType;
    private String remoteDestination;
    private int totalChunks;
    private int successfulChunks;
    private int failedChunks;
    private long totalBytesTransferred;
    private int totalRetries;
    private long durationMs;
    private TransferManifest manifest;
    private String errorMessage;

    @Builder.Default
    private Instant executedAt = Instant.now();
}
