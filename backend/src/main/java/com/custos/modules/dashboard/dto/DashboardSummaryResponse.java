package com.custos.modules.dashboard.dto;

import com.custos.modules.pipeline.entity.ExecutionStatus;
import com.custos.modules.pipeline.entity.TriggerType;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    private long totalPipelines;
    private long activePipelines;
    private List<PipelineSummaryDto> pipelines;
    private List<RecentExecutionDto> recentExecutions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PipelineSummaryDto {
        private UUID id;
        private String name;
        private boolean active;
        private String cronExpression;
        private Instant nextFireTime;
        private Instant lastRunAt;
        private ExecutionStatus lastStatus;
        private Long lastDurationMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentExecutionDto {
        private UUID id;
        private UUID pipelineId;
        private String pipelineName;
        private ExecutionStatus status;
        private Instant startTime;
        private Instant endTime;
        private Long durationMs;
        private String triggeredBy;
        private TriggerType triggerType;
        private String errorMessage;
    }
}
