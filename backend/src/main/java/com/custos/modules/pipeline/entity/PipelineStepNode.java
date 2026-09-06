package com.custos.modules.pipeline.entity;

import com.custos.shared.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "pipeline_step_nodes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStepNode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    @JsonIgnore
    private PipelineDefinition pipeline;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskDefinition task;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Column(name = "node_label", nullable = false, length = 150)
    private String nodeLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 50)
    private TaskType nodeType;

    @Column(name = "step_order", nullable = false)
    @Builder.Default
    private int stepOrder = 0;

    @Column(name = "position_x", nullable = false)
    @Builder.Default
    private double positionX = 0.0;

    @Column(name = "position_y", nullable = false)
    @Builder.Default
    private double positionY = 0.0;

    @Column(name = "config_override_json", columnDefinition = "TEXT")
    @Builder.Default
    private String configOverrideJson = "{}";

    @Column(name = "on_success_node_id")
    private UUID onSuccessNodeId;

    @Column(name = "on_failure_node_id")
    private UUID onFailureNodeId;
}
