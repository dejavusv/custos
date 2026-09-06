package com.custos.modules.transfer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferManifest {

    private String originalFileName;
    private String originalFilePath;
    private long originalSizeBytes;
    private String originalChecksumSha256;
    private long chunkSizeBytes;
    private int totalChunks;

    @Builder.Default
    private List<ChunkMetadata> chunks = new ArrayList<>();

    @Builder.Default
    private Instant createdAt = Instant.now();
}
