package com.custos.modules.pipeline.entity;

import com.custos.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pipeline_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineDefinition extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "timezone", nullable = false, length = 50)
    @Builder.Default
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(name = "misfire_policy", nullable = false, length = 50)
    @Builder.Default
    private MisfirePolicy misfirePolicy = MisfirePolicy.SMART_POLICY;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepOrder ASC")
    @Builder.Default
    private List<PipelineStepNode> nodes = new ArrayList<>();
}
