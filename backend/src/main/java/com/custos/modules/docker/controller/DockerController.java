package com.custos.modules.docker.controller;

import com.custos.modules.backup.dto.StorageBrowseResponse;
import com.custos.modules.docker.dto.DockerContainersResponse;
import com.custos.modules.docker.service.DockerContainerService;
import com.custos.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/docker")
@RequiredArgsConstructor
public class DockerController {

    private final DockerContainerService dockerContainerService;

    @GetMapping("/containers")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<DockerContainersResponse>> listContainers() {
        return ResponseEntity.ok(ApiResponse.ok(dockerContainerService.listContainers(), "Docker containers listed"));
    }

    @GetMapping("/containers/{name}/browse")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<StorageBrowseResponse>> browse(
            @PathVariable("name") String name,
            @RequestParam(value = "path", required = false) String path
    ) {
        return ResponseEntity.ok(ApiResponse.ok(dockerContainerService.browse(name, path), "Container directory listed successfully"));
    }
}
