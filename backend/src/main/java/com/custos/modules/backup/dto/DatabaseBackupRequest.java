package com.custos.modules.backup.dto;

import com.custos.modules.backup.model.CompressionFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseBackupRequest {

    // Vault profile reference (recommended)
    private UUID credentialId;

    // Or direct connection settings
    private String host;
    private Integer port;
    private String username;
    private String password;

    @NotBlank(message = "Database name is required")
    private String databaseName;

    // Optional: filter specific tables. If empty, dumps entire database.
    private List<String> tables;

    // Output destination directory
    private String destinationDir;

    private String customFileName;

    @Builder.Default
    private CompressionFormat compressionFormat = CompressionFormat.GZIP;

    @Builder.Default
    private int timeoutSeconds = 600;
}
