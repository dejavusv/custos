package com.custos.modules.pipeline.dto;

import com.custos.modules.pipeline.entity.MisfirePolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavePipelineRequest {

    private UUID id;

    @NotBlank(message = "Pipeline name is required")
    private String name;

    private String description;
    private String cronExpression;

    @Builder.Default
    private String timezone = "UTC";

    @Builder.Default
    private MisfirePolicy misfirePolicy = MisfirePolicy.SMART_POLICY;

    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private List<StepNodeDto> nodes = new ArrayList<>();
}
