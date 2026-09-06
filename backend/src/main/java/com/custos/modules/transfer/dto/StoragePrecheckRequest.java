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
public class StoragePrecheckRequest {

    @NotBlank(message = "Path to check is required")
    private String path;

    @Builder.Default
    private long requiredBytes = 100 * 1024 * 1024L; // Default 100MB

    @Builder.Default
    private double safetyMargin = 1.2; // 120%
}
