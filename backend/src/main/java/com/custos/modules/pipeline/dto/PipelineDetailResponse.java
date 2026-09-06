package com.custos.modules.pipeline.dto;

import com.custos.modules.pipeline.entity.MisfirePolicy;
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
public class PipelineDetailResponse {
    private UUID id;
    private String name;
    private String description;
    private String cronExpression;
    private String timezone;
    private MisfirePolicy misfirePolicy;
    private boolean isActive;
    private Instant nextFireTime;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private List<StepNodeDto> nodes = new ArrayList<>();
}
