package com.custos.modules.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionCleanupResult {

    private boolean success;
    private String directory;
    private int retentionDays;
    private int scannedFilesCount;
    private int deletedFilesCount;
    private long freedSpaceBytes;
    private List<String> deletedFileNames;
    private Instant executedAt;
    private String message;
}
