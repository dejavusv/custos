package com.custos.modules.pipeline.dto;

import com.custos.modules.pipeline.entity.ExecutionStatus;
import com.custos.modules.pipeline.entity.TriggerType;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineExecutionResponse {
    private UUID id;
    private UUID pipelineId;
    private String pipelineName;
    private ExecutionStatus status;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private String errorMessage;
    private String contextDataJson;
    private String triggeredBy;
    private TriggerType triggerType;

    @Builder.Default
    private List<StepLogDto> stepLogs = new ArrayList<>();
}
