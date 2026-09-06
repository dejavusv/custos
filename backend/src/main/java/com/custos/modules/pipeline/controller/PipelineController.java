package com.custos.modules.pipeline.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.pipeline.dto.PipelineDetailResponse;
import com.custos.modules.pipeline.dto.PipelineExecutionResponse;
import com.custos.modules.pipeline.dto.SavePipelineRequest;
import com.custos.modules.pipeline.service.PipelineService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.custos.modules.pipeline.entity.ExecutionStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<List<PipelineDetailResponse>> listPipelines() {
        return ResponseEntity.ok(pipelineService.listPipelines());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<PipelineDetailResponse> getPipeline(@PathVariable UUID id) {
        return ResponseEntity.ok(pipelineService.getPipeline(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<PipelineDetailResponse> createPipeline(
            @Valid @RequestBody SavePipelineRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        return new ResponseEntity<>(pipelineService.savePipeline(request, currentUser, servletRequest), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<PipelineDetailResponse> updatePipeline(
            @PathVariable UUID id,
            @Valid @RequestBody SavePipelineRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        request.setId(id);
        return ResponseEntity.ok(pipelineService.savePipeline(request, currentUser, servletRequest));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<Map<String, String>> deletePipeline(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        pipelineService.deletePipeline(id, currentUser, servletRequest);
        return ResponseEntity.ok(Map.of("message", "Pipeline deleted successfully", "pipelineId", id.toString()));
    }

    @PostMapping("/{id}/trigger")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<PipelineExecutionResponse> triggerPipeline(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(pipelineService.triggerNow(id, currentUser, servletRequest));
    }

    @PostMapping("/{id}/pause")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<Map<String, String>> pausePipeline(@PathVariable UUID id) {
        pipelineService.pausePipeline(id);
        return ResponseEntity.ok(Map.of("message", "Pipeline paused successfully", "pipelineId", id.toString()));
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<Map<String, String>> resumePipeline(@PathVariable UUID id) {
        pipelineService.resumePipeline(id);
        return ResponseEntity.ok(Map.of("message", "Pipeline resumed successfully", "pipelineId", id.toString()));
    }

    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<List<PipelineExecutionResponse>> listExecutions(@PathVariable UUID id) {
        return ResponseEntity.ok(pipelineService.listExecutions(id));
    }

    @GetMapping("/executions/all")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<List<PipelineExecutionResponse>> listAllExecutions(
            @RequestParam(required = false) ExecutionStatus status
    ) {
        return ResponseEntity.ok(pipelineService.listAllExecutions(status));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<PipelineExecutionResponse> getExecution(@PathVariable UUID executionId) {
        return ResponseEntity.ok(pipelineService.getExecution(executionId));
    }

    @PostMapping("/executions/{executionId}/abort")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<Map<String, Object>> abortExecution(@PathVariable UUID executionId) {
        boolean aborted = pipelineService.abortExecution(executionId);
        return ResponseEntity.ok(Map.of("aborted", aborted, "executionId", executionId.toString()));
    }
}
