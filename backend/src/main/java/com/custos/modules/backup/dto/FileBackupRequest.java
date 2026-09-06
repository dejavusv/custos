package com.custos.modules.backup.dto;

import com.custos.modules.backup.model.CompressionFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileBackupRequest {

    @NotBlank(message = "Source path is required")
    private String sourcePath;

    private String destinationDir;

    private String customFileName;

    @Builder.Default
    private CompressionFormat compressionFormat = CompressionFormat.TAR_GZ;

    // Glob patterns to exclude (e.g. ["**/node_modules/**", "**/*.log", "**/temp/**"])
    private List<String> exclusionPatterns;
}
