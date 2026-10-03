package com.custos.modules.backup.dto;

import com.custos.modules.backup.model.CompressionFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // Optional: name of a running Docker container to read sourcePath from (instead of the Custos server filesystem)
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_.-]*$", message = "Invalid Docker container name")
    private String dockerContainer;

    private String destinationDir;

    private String customFileName;

    @Builder.Default
    private CompressionFormat compressionFormat = CompressionFormat.TAR_GZ;

    // Glob patterns to exclude (e.g. ["**/node_modules/**", "**/*.log", "**/temp/**"])
    private List<String> exclusionPatterns;
}
