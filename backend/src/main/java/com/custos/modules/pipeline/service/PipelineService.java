package com.custos.modules.pipeline.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.pipeline.dto.*;
import com.custos.modules.pipeline.engine.DagCycleDetector;
import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.engine.ExecutionRegistry;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.repository.*;
import com.custos.modules.scheduling.service.QuartzSchedulerService;
import com.custos.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineDefinitionRepository pipelineDefinitionRepository;
    private final PipelineStepNodeRepository pipelineStepNodeRepository;
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final StepExecutionLogRepository stepExecutionLogRepository;
    private final TaskDefinitionRepository taskDefinitionRepository;
    private final DagCycleDetector dagCycleDetector;
    private final DagPipelineOrchestrator dagPipelineOrchestrator;
    private final QuartzSchedulerService quartzSchedulerService;
    private final ExecutionRegistry executionRegistry;
    private final AuditLogService auditLogService;

    @Transactional
    public PipelineDetailResponse savePipeline(SavePipelineRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        PipelineDefinition pipeline;
        boolean isNew = false;

        if (request.getId() != null) {
            pipeline = pipelineDefinitionRepository.findById(request.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + request.getId()));
            pipeline.setName(request.getName());
            pipeline.setDescription(request.getDescription());
            pipeline.setCronExpression(request.getCronExpression());
            pipeline.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
            pipeline.setMisfirePolicy(request.getMisfirePolicy() != null ? request.getMisfirePolicy() : MisfirePolicy.SMART_POLICY);
            pipeline.setActive(request.isActive());
        } else {
            isNew = true;
            if (pipelineDefinitionRepository.existsByName(request.getName())) {
                throw new IllegalArgumentException("Pipeline name already exists: " + request.getName());
            }
            pipeline = PipelineDefinition.builder()
                    .name(request.getName())
                    .description(request.getDescription())
                    .cronExpression(request.getCronExpression())
                    .timezone(request.getTimezone() != null ? request.getTimezone() : "UTC")
                    .misfirePolicy(request.getMisfirePolicy() != null ? request.getMisfirePolicy() : MisfirePolicy.SMART_POLICY)
                    .isActive(request.isActive())
                    .build();
        }

        pipeline = pipelineDefinitionRepository.save(pipeline);

        // Delete previous nodes and replace
        pipelineStepNodeRepository.deleteByPipelineId(pipeline.getId());

        List<PipelineStepNode> nodeEntities = new ArrayList<>();
        Map<String, PipelineStepNode> keyToNodeMap = new HashMap<>();

        if (request.getNodes() != null) {
            for (StepNodeDto dto : request.getNodes()) {
                PipelineStepNode node = PipelineStepNode.builder()
                        .pipeline(pipeline)
                        .nodeKey(dto.getNodeKey())
                        .nodeLabel(dto.getNodeLabel())
                        .nodeType(dto.getNodeType())
                        .stepOrder(dto.getStepOrder())
                        .positionX(dto.getPositionX())
                        .positionY(dto.getPositionY())
                        .configOverrideJson(dto.getConfigOverrideJson() != null ? dto.getConfigOverrideJson() : "{}")
                        .onSuccessNodeId(dto.getOnSuccessNodeId())
                        .onFailureNodeId(dto.getOnFailureNodeId())
                        .build();

                if (dto.getTaskId() != null) {
                    taskDefinitionRepository.findById(dto.getTaskId()).ifPresent(node::setTask);
                }

                // If DTO already has an ID, retain it so edge references match
                if (dto.getId() != null) {
                    node.setId(dto.getId());
                }

                node = pipelineStepNodeRepository.save(node);
                nodeEntities.add(node);
                keyToNodeMap.put(node.getNodeKey(), node);
            }
        }

        // Validate cycles
        dagCycleDetector.validateNodes(nodeEntities);

        // Sync with Quartz
        try {
            if (pipeline.isActive() && pipeline.getCronExpression() != null && !pipeline.getCronExpression().trim().isEmpty()) {
                quartzSchedulerService.schedulePipeline(pipeline);
            } else {
                quartzSchedulerService.unschedulePipeline(pipeline.getId());
            }
        } catch (Exception e) {
            log.error("Failed to sync Quartz schedule for pipeline {}: {}", pipeline.getId(), e.getMessage());
        }

        auditLogService.logFromRequest(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                isNew ? "CREATE_PIPELINE" : "UPDATE_PIPELINE",
                "PIPELINE",
                "Saved pipeline: " + pipeline.getName(),
                servletRequest
        );

        return mapToDetailResponse(pipeline, nodeEntities);
    }

    @Transactional(readOnly = true)
    public PipelineDetailResponse getPipeline(UUID id) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));
        List<PipelineStepNode> nodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(id);
        return mapToDetailResponse(pipeline, nodes);
    }

    @Transactional(readOnly = true)
    public List<PipelineDetailResponse> listPipelines() {
        return pipelineDefinitionRepository.findAll().stream()
                .map(p -> {
                    List<PipelineStepNode> nodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(p.getId());
                    return mapToDetailResponse(p, nodes);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePipeline(UUID id, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));

        try {
            quartzSchedulerService.unschedulePipeline(id);
        } catch (Exception e) {
            log.warn("Error unscheduling pipeline before deletion: {}", e.getMessage());
        }

        pipelineDefinitionRepository.delete(pipeline);

        auditLogService.logFromRequest(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                "DELETE_PIPELINE",
                "PIPELINE",
                "Deleted pipeline: " + pipeline.getName(),
                servletRequest
        );
    }

    public PipelineExecutionResponse triggerNow(UUID id, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));

        String username = currentUser != null ? currentUser.getUsername() : "USER";

        // Asynchronously run pipeline
        CompletableFuture.runAsync(() -> {
            try {
                dagPipelineOrchestrator.executePipeline(id, username, TriggerType.MANUAL);
            } catch (Exception e) {
                log.error("Error running manual execution for pipeline {}: {}", id, e.getMessage(), e);
            }
        });

        auditLogService.logFromRequest(
                currentUser != null ? currentUser.getId() : null,
                username,
                "TRIGGER_PIPELINE",
                "PIPELINE",
                "Triggered pipeline: " + pipeline.getName(),
                servletRequest
        );

        return PipelineExecutionResponse.builder()
                .pipelineId(id)
                .pipelineName(pipeline.getName())
                .status(ExecutionStatus.RUNNING)
                .startTime(java.time.Instant.now())
                .triggeredBy(username)
                .triggerType(TriggerType.MANUAL)
                .build();
    }

    public void pausePipeline(UUID id) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));
        pipeline.setActive(false);
        pipelineDefinitionRepository.save(pipeline);
        try {
            quartzSchedulerService.pausePipeline(id);
        } catch (Exception e) {
            log.error("Failed to pause pipeline in Quartz: {}", e.getMessage());
        }
    }

    public void resumePipeline(UUID id) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + id));
        pipeline.setActive(true);
        pipelineDefinitionRepository.save(pipeline);
        try {
            quartzSchedulerService.resumePipeline(id);
        } catch (Exception e) {
            log.error("Failed to resume pipeline in Quartz: {}", e.getMessage());
        }
    }

    public boolean abortExecution(UUID executionId) {
        boolean aborted = executionRegistry.abortExecution(executionId);
        pipelineExecutionRepository.findById(executionId).ifPresent(exec -> {
            exec.setStatus(ExecutionStatus.ABORTED);
            exec.setEndTime(java.time.Instant.now());
            exec.setErrorMessage("Execution aborted by user");
            pipelineExecutionRepository.save(exec);
        });
        return aborted;
    }

    @Transactional(readOnly = true)
    public PipelineExecutionResponse getExecution(UUID executionId) {
        PipelineExecution exec = pipelineExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found: " + executionId));
        List<StepExecutionLog> logs = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(executionId);
        return mapToExecutionResponse(exec, logs);
    }

    @Transactional(readOnly = true)
    public List<PipelineExecutionResponse> listExecutions(UUID pipelineId) {
        return pipelineExecutionRepository.findByPipelineIdOrderByCreatedAtDesc(pipelineId).stream()
                .map(exec -> {
                    List<StepExecutionLog> logs = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(exec.getId());
                    return mapToExecutionResponse(exec, logs);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PipelineExecutionResponse> listAllExecutions(ExecutionStatus status) {
        List<PipelineExecution> list = (status != null)
                ? pipelineExecutionRepository.findByStatus(status)
                : pipelineExecutionRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return list.stream()
                .map(exec -> {
                    List<StepExecutionLog> logs = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(exec.getId());
                    return mapToExecutionResponse(exec, logs);
                })
                .collect(Collectors.toList());
    }

    private PipelineDetailResponse mapToDetailResponse(PipelineDefinition pipeline, List<PipelineStepNode> nodes) {
        List<StepNodeDto> nodeDtos = nodes.stream()
                .map(n -> StepNodeDto.builder()
                        .id(n.getId())
                        .taskId(n.getTask() != null ? n.getTask().getId() : null)
                        .nodeKey(n.getNodeKey())
                        .nodeLabel(n.getNodeLabel())
                        .nodeType(n.getNodeType())
                        .stepOrder(n.getStepOrder())
                        .positionX(n.getPositionX())
                        .positionY(n.getPositionY())
                        .configOverrideJson(n.getConfigOverrideJson())
                        .onSuccessNodeId(n.getOnSuccessNodeId())
                        .onFailureNodeId(n.getOnFailureNodeId())
                        .build())
                .collect(Collectors.toList());

        return PipelineDetailResponse.builder()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .description(pipeline.getDescription())
                .cronExpression(pipeline.getCronExpression())
                .timezone(pipeline.getTimezone())
                .misfirePolicy(pipeline.getMisfirePolicy())
                .isActive(pipeline.isActive())
                .nextFireTime(quartzSchedulerService.getNextFireTime(pipeline.getId()))
                .createdAt(pipeline.getCreatedAt())
                .updatedAt(pipeline.getUpdatedAt())
                .nodes(nodeDtos)
                .build();
    }

    private PipelineExecutionResponse mapToExecutionResponse(PipelineExecution exec, List<StepExecutionLog> logs) {
        List<StepLogDto> logDtos = logs.stream()
                .map(l -> StepLogDto.builder()
                        .id(l.getId())
                        .stepNodeId(l.getStepNodeId())
                        .stepName(l.getStepName())
                        .status(l.getStatus())
                        .startTime(l.getStartTime())
                        .endTime(l.getEndTime())
                        .durationMs(l.getDurationMs())
                        .logsText(l.getLogsText())
                        .fileSizeBytes(l.getFileSizeBytes())
                        .outputPath(l.getOutputPath())
                        .checksumSha256(l.getChecksumSha256())
                        .awsSesMessageId(l.getAwsSesMessageId())
                        .errorMessage(l.getErrorMessage())
                        .build())
                .collect(Collectors.toList());

        return PipelineExecutionResponse.builder()
                .id(exec.getId())
                .pipelineId(exec.getPipeline().getId())
                .pipelineName(exec.getPipeline().getName())
                .status(exec.getStatus())
                .startTime(exec.getStartTime())
                .endTime(exec.getEndTime())
                .durationMs(exec.getDurationMs())
                .errorMessage(exec.getErrorMessage())
                .contextDataJson(exec.getContextDataJson())
                .triggeredBy(exec.getTriggeredBy() != null ? exec.getTriggeredBy() : "SYSTEM")
                .triggerType(exec.getTriggerType())
                .stepLogs(logDtos)
                .build();
    }
}
