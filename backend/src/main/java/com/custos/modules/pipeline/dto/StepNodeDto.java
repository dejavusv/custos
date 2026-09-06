package com.custos.modules.pipeline.dto;

import com.custos.modules.pipeline.entity.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepNodeDto {
    private UUID id;
    private UUID taskId;

    @NotBlank(message = "Node key is required")
    private String nodeKey;

    @NotBlank(message = "Node label is required")
    private String nodeLabel;

    @NotNull(message = "Node type is required")
    private TaskType nodeType;

    private int stepOrder;
    private double positionX;
    private double positionY;
    private String configOverrideJson;
    private UUID onSuccessNodeId;
    private UUID onFailureNodeId;
}
