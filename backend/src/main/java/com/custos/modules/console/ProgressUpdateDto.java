package com.custos.modules.console;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressUpdateDto {
    private UUID executionId;
    private UUID stepNodeId;
    private String stepName;
    private int percentage;
    private int currentChunk;
    private int totalChunks;
    private long bytesTransferred;
    private long totalBytes;
    private String status;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
