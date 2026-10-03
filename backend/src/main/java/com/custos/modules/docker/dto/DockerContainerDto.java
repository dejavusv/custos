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
public class DockerContainerDto {
    private String name;
    private String image;
    private String status;
    private List<String> networks;
}
