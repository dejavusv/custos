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
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
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

        String cron = request.getCronExpression();
        if (cron != null && !cron.trim().isEmpty() && !CronExpression.isValidExpression(cron.trim())) {
            throw new BadRequestException("Invalid cron expression: " + cron);
        }

        if (request.getId() != null) {
            pipeline = pipelineDefinitionRepository.findById(request.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + request.getId()));
            if (pipelineDefinitionRepository.existsByNameAndIdNot(request.getName(), pipeline.getId())) {
                throw new BadRequestException("Pipeline name already exists: " + request.getName());
            }
            pipeline.setName(request.getName());
            pipeline.setDescription(request.getDescription());
            pipeline.setCronExpression(request.getCronExpression());
            pipeline.setTimezone(request.getTimezone() != null ? request.getTimezone() : "UTC");
            pipeline.setMisfirePolicy(request.getMisfirePolicy() != null ? request.getMisfirePolicy() : MisfirePolicy.SMART_POLICY);
            pipeline.setActive(request.isActive());
        } else {
            isNew = true;
            if (pipelineDefinitionRepository.existsByName(request.getName())) {
                throw new BadRequestException("Pipeline name already exists: " + request.getName());
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

        List<StepNodeDto> dtos = request.getNodes() != null ? request.getNodes() : List.of();
        Set<String> seenKeys = new HashSet<>();
        for (StepNodeDto dto : dtos) {
            if (!seenKeys.add(dto.getNodeKey())) {
                throw new BadRequestException("Duplicate node key in pipeline: " + dto.getNodeKey());
            }
        }

        long startCount = dtos.stream().filter(d -> d.getNodeType() == TaskType.START).count();
        if (startCount > 1) {
            throw new BadRequestException("A pipeline can have only one Start node");
        }

        // Upsert: Node เดิมอัปเดตในที่ (คง ID ไว้ให้ประวัติการรันยังอ้างถึงได้), Node ใหม่ให้ DB สร้าง ID เอง
        List<PipelineStepNode> existingNodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(pipeline.getId());
        Map<UUID, PipelineStepNode> existingById = new HashMap<>();
        for (PipelineStepNode existing : existingNodes) {
            existingById.put(existing.getId(), existing);
        }

        List<PipelineStepNode> nodeEntities = new ArrayList<>();
        Map<String, PipelineStepNode> keyToNodeMap = new HashMap<>();
        for (StepNodeDto dto : dtos) {
            PipelineStepNode node = dto.getId() != null ? existingById.remove(dto.getId()) : null;
            if (node == null) {
                node = PipelineStepNode.builder().pipeline(pipeline).build();
            }
            node.setNodeKey(dto.getNodeKey());
            node.setNodeLabel(dto.getNodeLabel());
            node.setNodeType(dto.getNodeType());
            node.setStepOrder(dto.getStepOrder());
            node.setPositionX(dto.getPositionX());
            node.setPositionY(dto.getPositionY());
            node.setConfigOverrideJson(dto.getConfigOverrideJson() != null ? dto.getConfigOverrideJson() : "{}");
            node.setTask(dto.getTaskId() != null ? taskDefinitionRepository.findById(dto.getTaskId()).orElse(null) : null);
            // เส้นเชื่อมจะกำหนดในรอบที่สอง หลังทุก Node มี ID แล้ว (เลี่ยง FK on_success/on_failure_node_id)
            node.setOnSuccessNodeId(null);
            node.setOnFailureNodeId(null);

            nodeEntities.add(node);
            keyToNodeMap.put(node.getNodeKey(), node);
        }

        // Node ที่เหลือใน existingById คือ Node ที่ถูกลบออกจาก Builder
        nodeEntities = pipelineStepNodeRepository.saveAllAndFlush(nodeEntities);
        pipelineStepNodeRepository.deleteAll(existingById.values());
        pipelineStepNodeRepository.flush();

        Set<UUID> savedIds = nodeEntities.stream().map(PipelineStepNode::getId).collect(Collectors.toSet());
        for (int i = 0; i < dtos.size(); i++) {
            StepNodeDto dto = dtos.get(i);
            PipelineStepNode node = nodeEntities.get(i);
            node.setOnSuccessNodeId(resolveEdgeTarget(dto.getOnSuccessNodeKey(), dto.getOnSuccessNodeId(), keyToNodeMap, savedIds));
            node.setOnFailureNodeId(resolveEdgeTarget(dto.getOnFailureNodeKey(), dto.getOnFailureNodeId(), keyToNodeMap, savedIds));
        }

        // Start ต้องไม่มีเส้นเข้า, Stop ต้องไม่มีเส้นออก
        Set<UUID> startIds = nodeEntities.stream()
                .filter(n -> n.getNodeType() == TaskType.START)
                .map(PipelineStepNode::getId)
                .collect(Collectors.toSet());
        for (PipelineStepNode node : nodeEntities) {
            if (node.getNodeType() == TaskType.STOP
                    && (node.getOnSuccessNodeId() != null || node.getOnFailureNodeId() != null)) {
                throw new BadRequestException("Stop node cannot have outgoing connections: " + node.getNodeKey());
            }
            if ((node.getOnSuccessNodeId() != null && startIds.contains(node.getOnSuccessNodeId()))
                    || (node.getOnFailureNodeId() != null && startIds.contains(node.getOnFailureNodeId()))) {
                throw new BadRequestException("Start node cannot be the target of a connection (from " + node.getNodeKey() + ")");
            }
        }

        // Validate cycles
        dagCycleDetector.validateNodes(nodeEntities);
        nodeEntities = pipelineStepNodeRepository.saveAll(nodeEntities);

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

    private UUID resolveEdgeTarget(String targetKey, UUID targetId, Map<String, PipelineStepNode> keyToNodeMap, Set<UUID> savedIds) {
        if (targetKey != null && !targetKey.isBlank()) {
            PipelineStepNode target = keyToNodeMap.get(targetKey);
            if (target == null) {
                throw new BadRequestException("Edge points to unknown node key: " + targetKey);
            }
            return target.getId();
        }
        // รองรับ client เดิมที่ส่ง UUID มาตรงๆ — ยอมรับเฉพาะ ID ของ Node ที่อยู่ใน Pipeline นี้
        return targetId != null && savedIds.contains(targetId) ? targetId : null;
    }

    private static final double MOVE_VERTICAL_GAP = 200.0;

    /**
     * ย้าย Node ที่เลือกจาก Pipeline ต้นทางไป Pipeline ปลายทาง โดยคง ID/Task/Config ไว้
     * เส้นเชื่อมระหว่าง Node ที่ย้ายด้วยกันจะติดไปด้วย ส่วนเส้นที่ข้ามระหว่างกลุ่มที่ย้ายกับกลุ่มที่อยู่ต่อจะถูกตัดออก
     */
    @Transactional
    public MoveNodesResponse moveNodes(UUID sourceId, MoveNodesRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        UUID targetId = request.getTargetPipelineId();
        if (sourceId.equals(targetId)) {
            throw new BadRequestException("Source and target pipeline must be different");
        }
        PipelineDefinition source = pipelineDefinitionRepository.findById(sourceId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + sourceId));
        PipelineDefinition target = pipelineDefinitionRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline not found: " + targetId));

        List<PipelineStepNode> sourceNodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(sourceId);
        List<PipelineStepNode> targetNodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(targetId);

        Map<UUID, PipelineStepNode> sourceById = new HashMap<>();
        for (PipelineStepNode n : sourceNodes) {
            sourceById.put(n.getId(), n);
        }
        Set<UUID> selected = new LinkedHashSet<>(request.getNodeIds());
        for (UUID id : selected) {
            PipelineStepNode n = sourceById.get(id);
            if (n == null) {
                throw new BadRequestException("Node does not belong to the source pipeline: " + id);
            }
            if (n.getNodeType() == TaskType.START) {
                throw new BadRequestException("Start node cannot be moved to another pipeline");
            }
        }

        // ตัดเส้นที่ข้ามระหว่างกลุ่มที่ย้าย กับกลุ่มที่อยู่ต่อ
        for (PipelineStepNode n : sourceNodes) {
            boolean moving = selected.contains(n.getId());
            n.setOnSuccessNodeId(keepEdgeWithinGroup(n.getOnSuccessNodeId(), moving, selected));
            n.setOnFailureNodeId(keepEdgeWithinGroup(n.getOnFailureNodeId(), moving, selected));
        }

        List<PipelineStepNode> moving = sourceNodes.stream()
                .filter(n -> selected.contains(n.getId()))
                .collect(Collectors.toList());

        // nodeKey ต้องไม่ซ้ำใน Pipeline ปลายทาง — ถ้าซ้ำให้ต่อท้าย _2, _3 ...
        Set<String> usedKeys = targetNodes.stream().map(PipelineStepNode::getNodeKey).collect(Collectors.toCollection(HashSet::new));
        Set<String> movingOriginalKeys = moving.stream().map(PipelineStepNode::getNodeKey).collect(Collectors.toSet());
        Map<String, String> renamedKeys = new HashMap<>();
        for (PipelineStepNode n : moving) {
            String key = n.getNodeKey();
            if (usedKeys.contains(key)) {
                int suffix = 2;
                String candidate = key + "_" + suffix;
                while (usedKeys.contains(candidate) || movingOriginalKeys.contains(candidate)) {
                    candidate = key + "_" + (++suffix);
                }
                renamedKeys.put(key, candidate);
                key = candidate;
            }
            usedKeys.add(key);
        }

        double offsetY = 0.0;
        int nextStepOrder = 0;
        if (!targetNodes.isEmpty()) {
            double targetMaxY = targetNodes.stream().mapToDouble(PipelineStepNode::getPositionY).max().orElse(0.0);
            double movingMinY = moving.stream().mapToDouble(PipelineStepNode::getPositionY).min().orElse(0.0);
            offsetY = targetMaxY + MOVE_VERTICAL_GAP - movingMinY;
            nextStepOrder = targetNodes.stream().mapToInt(PipelineStepNode::getStepOrder).max().orElse(-1) + 1;
        }

        for (PipelineStepNode n : moving) {
            String newKey = renamedKeys.get(n.getNodeKey());
            n.setPipeline(target);
            n.setPositionY(n.getPositionY() + offsetY);
            n.setStepOrder(nextStepOrder++);
            if (newKey != null) {
                n.setNodeKey(newKey);
            }
        }
        // อัปเดต placeholder ${oldKey.xxx} ใน config ของ Node ที่ถูกเปลี่ยน key
        if (!renamedKeys.isEmpty()) {
            for (PipelineStepNode n : moving) {
                String json = n.getConfigOverrideJson();
                if (json == null) {
                    continue;
                }
                for (Map.Entry<String, String> e : renamedKeys.entrySet()) {
                    json = json.replace("${" + e.getKey() + ".", "${" + e.getValue() + ".");
                }
                n.setConfigOverrideJson(json);
            }
        }

        List<PipelineStepNode> remaining = sourceNodes.stream().filter(n -> !selected.contains(n.getId())).collect(Collectors.toList());
        List<PipelineStepNode> targetAll = new ArrayList<>(targetNodes);
        targetAll.addAll(moving);

        dagCycleDetector.validateNodes(remaining);
        dagCycleDetector.validateNodes(targetAll);
        pipelineStepNodeRepository.saveAll(sourceNodes);
        pipelineStepNodeRepository.flush();

        auditLogService.logFromRequest(
                currentUser != null ? currentUser.getId() : null,
                currentUser != null ? currentUser.getUsername() : "SYSTEM",
                "MOVE_PIPELINE_NODES",
                "PIPELINE",
                "Moved " + moving.size() + " node(s) from pipeline '" + source.getName() + "' to '" + target.getName() + "'",
                servletRequest
        );

        return MoveNodesResponse.builder()
                .source(mapToDetailResponse(source, remaining))
                .target(mapToDetailResponse(target, pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(targetId)))
                .movedCount(moving.size())
                .build();
    }

    private UUID keepEdgeWithinGroup(UUID edgeTarget, boolean sourceIsMoving, Set<UUID> selected) {
        if (edgeTarget == null) {
            return null;
        }
        return selected.contains(edgeTarget) == sourceIsMoving ? edgeTarget : null;
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
