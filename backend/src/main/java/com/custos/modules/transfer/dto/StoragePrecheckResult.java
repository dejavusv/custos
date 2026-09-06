package com.custos.modules.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoragePrecheckResult {

    private String targetPath;
    private long usableSpaceBytes;
    private long totalSpaceBytes;
    private long requiredBytes;
    private double safetyMargin;
    private boolean hasEnoughSpace;
    private double freeSpacePercentage;
    private String message;
}
