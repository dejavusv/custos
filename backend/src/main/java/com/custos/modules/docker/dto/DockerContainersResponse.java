package com.custos.modules.docker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerContainersResponse {
    // false when Docker integration is disabled or the Docker Engine cannot be reached
    private boolean available;
    private String message;
    private List<DockerContainerDto> containers;
}
