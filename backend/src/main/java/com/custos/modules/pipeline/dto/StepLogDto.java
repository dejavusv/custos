package com.custos.modules.pipeline.dto;

import com.custos.modules.pipeline.entity.ExecutionStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepLogDto {
    private UUID id;
    private UUID stepNodeId;
    private String stepName;
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String logsText;
    private Long fileSizeBytes;
    private String outputPath;
    private String checksumSha256;
    private String awsSesMessageId;
    private String errorMessage;
}
