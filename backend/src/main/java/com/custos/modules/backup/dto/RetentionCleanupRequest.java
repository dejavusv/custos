package com.custos.modules.backup.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionCleanupRequest {

    @NotBlank(message = "Backup directory is required")
    private String backupDirectory;

    @Min(value = 0, message = "Retention days must be at least 0")
    @Builder.Default
    private int retentionDays = 30;

    // Optional glob filter, e.g. "*.tar.gz" or "*.sql.gz"
    @Builder.Default
    private String filePattern = "*";
}
