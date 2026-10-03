package com.custos.modules.pipeline.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveNodesRequest {

    @NotNull(message = "Target pipeline is required")
    private UUID targetPipelineId;

    @NotEmpty(message = "Select at least one task to move")
    private List<UUID> nodeIds;
}
