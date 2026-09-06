package com.custos.modules.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitFileRequest {

    @NotBlank(message = "Source file path is required")
    private String sourceFilePath;

    private String outputDirectory;

    @Builder.Default
    private long chunkSizeBytes = 50 * 1024 * 1024L; // Default 50MB
}
