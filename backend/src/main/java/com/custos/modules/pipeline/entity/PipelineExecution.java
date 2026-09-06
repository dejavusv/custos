package com.custos.modules.pipeline.entity;

import com.custos.shared.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipeline_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineExecution extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    @JsonIgnore
    private PipelineDefinition pipeline;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.PENDING;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "context_data_json", columnDefinition = "TEXT")
    @Builder.Default
    private String contextDataJson = "{}";

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    @Builder.Default
    private TriggerType triggerType = TriggerType.MANUAL;

    @OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("startTime ASC")
    @Builder.Default
    private List<StepExecutionLog> stepLogs = new ArrayList<>();
}
