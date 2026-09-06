package com.custos.modules.pipeline.entity;

import com.custos.shared.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "step_execution_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepExecutionLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    @JsonIgnore
    private PipelineExecution execution;

    @Column(name = "step_node_id")
    private UUID stepNodeId;

    @Column(name = "step_name", nullable = false, length = 150)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ExecutionStatus status;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "logs_text", columnDefinition = "TEXT")
    private String logsText;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "output_path", length = 1000)
    private String outputPath;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "aws_ses_message_id", length = 100)
    private String awsSesMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
