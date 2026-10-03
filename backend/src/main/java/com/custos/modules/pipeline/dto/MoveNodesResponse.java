package com.custos.modules.pipeline.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveNodesResponse {
    private PipelineDetailResponse source;
    private PipelineDetailResponse target;
    private int movedCount;
}
