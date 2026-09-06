package com.custos.modules.transfer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkMetadata {

    private int partNumber;
    private String fileName;
    private String filePath;
    private long sizeBytes;
    private String checksumSha256;

    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private int retries = 0;
}
