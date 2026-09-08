package com.custos.modules.pipeline.engine;

import com.custos.modules.backup.dto.DatabaseBackupRequest;
import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.backup.service.BackupService;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.pipeline.repository.PipelineStepNodeRepository;
import com.custos.modules.pipeline.repository.StepExecutionLogRepository;
import com.custos.modules.transfer.dto.TransferRequest;
import com.custos.modules.transfer.model.TransferResult;
import com.custos.modules.transfer.service.ResilientTransferService;
import com.custos.shared.ResourceNotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DagPipelineOrchestrator {

    private final PipelineDefinitionRepository pipelineDefinitionRepository;
    private final PipelineStepNodeRepository pipelineStepNodeRepository;
    private final PipelineExecutionRepository pipelineExecutionRepository;
    private final StepExecutionLogRepository stepExecutionLogRepository;
    private final DagCycleDetector dagCycleDetector;
    private final ExecutionRegistry executionRegistry;
    private final BackupService backupService;
    private final ResilientTransferService resilientTransferService;
    private final ObjectMapper objectMapper;
    private final com.custos.modules.console.ExecutionProgressBroadcaster progressBroadcaster;
    private final com.custos.modules.notification.service.NotificationService notificationService;

    /**
     * Synchronously or asynchronously runs a full pipeline execution.
     */
    public PipelineExecution executePipeline(UUID pipelineId, String triggeredBy, TriggerType triggerType) {
        PipelineDefinition pipeline = pipelineDefinitionRepository.findById(pipelineId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline definition not found: " + pipelineId));

        List<PipelineStepNode> nodes = pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(pipelineId);

        // 1. Cycle Detection
        dagCycleDetector.validateNodes(nodes);

        // 2. Initialize Execution Record
        PipelineExecution execution = PipelineExecution.builder()
                .pipeline(pipeline)
                .status(ExecutionStatus.RUNNING)
                .startTime(Instant.now())
                .triggeredBy(triggeredBy != null ? triggeredBy : "SYSTEM")
                .triggerType(triggerType != null ? triggerType : TriggerType.MANUAL)
                .build();
        execution = pipelineExecutionRepository.save(execution);

        PipelineExecutionContext context = new PipelineExecutionContext();
        executionRegistry.registerExecution(execution.getId(), null, context);

        log.info("Started execution {} for pipeline: {}", execution.getId(), pipeline.getName());

        if (nodes.isEmpty()) {
            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setEndTime(Instant.now());
            execution.setDurationMs(Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());
            executionRegistry.unregisterExecution(execution.getId());
            return pipelineExecutionRepository.save(execution);
        }

        // Build node lookup map
        Map<UUID, PipelineStepNode> nodeMap = new HashMap<>();
        Set<UUID> targetNodes = new HashSet<>();
        for (PipelineStepNode node : nodes) {
            nodeMap.put(node.getId(), node);
            if (node.getOnSuccessNodeId() != null) targetNodes.add(node.getOnSuccessNodeId());
            if (node.getOnFailureNodeId() != null) targetNodes.add(node.getOnFailureNodeId());
        }

        // Identify starting root node (first node that is not a target of another node, or min stepOrder)
        PipelineStepNode currentNode = null;
        for (PipelineStepNode node : nodes) {
            if (!targetNodes.contains(node.getId())) {
                currentNode = node;
                break;
            }
        }
        if (currentNode == null) {
            currentNode = nodes.get(0);
        }

        boolean pipelineFailed = false;
        String failureReason = null;
        Set<UUID> executedNodeIds = new HashSet<>();

        while (currentNode != null) {
            if (context.isCancelled() || executionRegistry.isAborted(execution.getId())) {
                log.warn("Execution {} was aborted. Halting pipeline.", execution.getId());
                execution.setStatus(ExecutionStatus.ABORTED);
                execution.setErrorMessage("Execution aborted by user or system");
                break;
            }

            executedNodeIds.add(currentNode.getId());
            progressBroadcaster.broadcastLog(execution.getId(), currentNode.getNodeLabel(), "INFO",
                    "Started step: " + currentNode.getNodeLabel() + " (" + currentNode.getNodeType() + ")");
            progressBroadcaster.broadcastProgress(com.custos.modules.console.ProgressUpdateDto.builder()
                    .executionId(execution.getId())
                    .stepNodeId(currentNode.getId())
                    .stepName(currentNode.getNodeLabel())
                    .status("RUNNING")
                    .percentage(Math.min(95, (executedNodeIds.size() * 100) / nodes.size()))
                    .build());

            StepExecutionLog stepLog = StepExecutionLog.builder()
                    .execution(execution)
                    .stepNodeId(currentNode.getId())
                    .stepName(currentNode.getNodeLabel())
                    .status(ExecutionStatus.RUNNING)
                    .startTime(Instant.now())
                    .build();
            stepLog = stepExecutionLogRepository.save(stepLog);

            boolean stepSuccess = false;
            try {
                executeStep(currentNode, context, stepLog, execution.getId());
                stepSuccess = true;
                stepLog.setStatus(ExecutionStatus.SUCCESS);
                progressBroadcaster.broadcastLog(execution.getId(), currentNode.getNodeLabel(), "SUCCESS",
                        "Step completed successfully");
            } catch (Exception e) {
                log.error("Step '{}' failed: {}", currentNode.getNodeLabel(), e.getMessage());
                stepSuccess = false;
                stepLog.setStatus(ExecutionStatus.FAILED);
                stepLog.setErrorMessage(e.getMessage());
                failureReason = "Step failed: " + currentNode.getNodeLabel() + " - " + e.getMessage();
                progressBroadcaster.broadcastLog(execution.getId(), currentNode.getNodeLabel(), "ERROR",
                        "Step failed: " + e.getMessage());
            } finally {
                stepLog.setEndTime(Instant.now());
                stepLog.setDurationMs(Duration.between(stepLog.getStartTime(), stepLog.getEndTime()).toMillis());
                stepExecutionLogRepository.save(stepLog);
            }

            // Routing logic
            if (stepSuccess) {
                UUID nextId = currentNode.getOnSuccessNodeId();
                if (nextId != null && nodeMap.containsKey(nextId) && !executedNodeIds.contains(nextId)) {
                    currentNode = nodeMap.get(nextId);
                } else {
                    // Check if there are other unexecuted nodes in pipeline sequentially
                    currentNode = findNextSequentialNode(nodes, executedNodeIds);
                }
            } else {
                UUID failId = currentNode.getOnFailureNodeId();
                if (failId != null && nodeMap.containsKey(failId) && !executedNodeIds.contains(failId)) {
                    log.warn("Routing step failure to on_failure branch: {}", failId);
                    currentNode = nodeMap.get(failId);
                } else {
                    pipelineFailed = true;
                    currentNode = null;
                }
            }
        }

        // Finalize Execution
        execution.setEndTime(Instant.now());
        execution.setDurationMs(Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis());

        if (execution.getStatus() == ExecutionStatus.ABORTED) {
            // Already set
        } else if (pipelineFailed) {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage(failureReason);
        } else {
            execution.setStatus(ExecutionStatus.SUCCESS);
        }

        try {
            Map<String, Object> allData = new HashMap<>();
            allData.put("variables", context.getAllVariables());
            allData.put("metrics", context.getAllMetrics());
            execution.setContextDataJson(objectMapper.writeValueAsString(allData));
        } catch (Exception ignored) {
        }

        executionRegistry.unregisterExecution(execution.getId());
        execution = pipelineExecutionRepository.save(execution);
        log.info("Finished execution {} with status: {}", execution.getId(), execution.getStatus());
        return execution;
    }

    private PipelineStepNode findNextSequentialNode(List<PipelineStepNode> nodes, Set<UUID> executedNodeIds) {
        for (PipelineStepNode node : nodes) {
            if (!executedNodeIds.contains(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private void executeStep(PipelineStepNode node, PipelineExecutionContext context, StepExecutionLog stepLog, UUID executionId) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfigOverrideJson());

        switch (node.getNodeType()) {
            case DATABASE_BACKUP -> {
                CompressionFormat compFormat = CompressionFormat.GZIP;
                if (config.get("compressionFormat") != null && !config.get("compressionFormat").toString().isBlank()) {
                    try {
                        compFormat = CompressionFormat.valueOf(config.get("compressionFormat").toString().toUpperCase());
                    } catch (Exception ignored) {}
                }

                List<String> tableList = null;
                if (config.get("tables") != null && !config.get("tables").toString().isBlank()) {
                    tableList = Arrays.stream(config.get("tables").toString().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                DatabaseBackupRequest req = DatabaseBackupRequest.builder()
                        .databaseName(context.resolvePlaceholders((String) config.getOrDefault("databaseName", "")))
                        .host(context.resolvePlaceholders((String) config.getOrDefault("host", "localhost")))
                        .port(config.get("port") != null && !config.get("port").toString().isBlank() ? Integer.parseInt(config.get("port").toString()) : 5432)
                        .username(context.resolvePlaceholders((String) config.getOrDefault("username", "")))
                        .password((String) config.getOrDefault("password", ""))
                        .tables(tableList)
                        .destinationDir(context.resolvePlaceholders((String) config.getOrDefault("destinationDir", "storage/backups")))
                        .customFileName(context.resolvePlaceholders((String) config.get("customFileName")))
                        .compressionFormat(compFormat)
                        .build();

                if (config.get("credentialId") != null && !config.get("credentialId").toString().isBlank()) {
                    try {
                        req.setCredentialId(UUID.fromString(config.get("credentialId").toString()));
                    } catch (Exception e) {
                        log.warn("Invalid credentialId UUID in database backup step: {}", config.get("credentialId"));
                    }
                }

                BackupResult result = backupService.backupDatabase(req, null, null);
                stepLog.setOutputPath(result.getDestinationPath());
                stepLog.setFileSizeBytes(result.getFileSizeBytes());
                stepLog.setChecksumSha256(result.getChecksumSha256());
                stepLog.setLogsText(result.isSuccess() ? "Database backup successful: " + result.getDestinationPath() : result.getErrorMessage());

                context.setStepOutput(node.getNodeKey(), result.getDestinationPath(), result.getChecksumSha256(), result.getFileSizeBytes());
            }

            case FILE_BACKUP -> {
                CompressionFormat compFormat = CompressionFormat.TAR_GZ;
                if (config.get("compressionFormat") != null && !config.get("compressionFormat").toString().isBlank()) {
                    try {
                        compFormat = CompressionFormat.valueOf(config.get("compressionFormat").toString().toUpperCase());
                    } catch (Exception ignored) {}
                }

                List<String> exclusionList = null;
                if (config.get("exclusionPatterns") != null && !config.get("exclusionPatterns").toString().isBlank()) {
                    exclusionList = Arrays.stream(config.get("exclusionPatterns").toString().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                }

                FileBackupRequest req = FileBackupRequest.builder()
                        .sourcePath(context.resolvePlaceholders((String) config.getOrDefault("sourcePath", "")))
                        .destinationDir(context.resolvePlaceholders((String) config.getOrDefault("destinationDir", "storage/backups")))
                        .customFileName(context.resolvePlaceholders((String) config.get("customFileName")))
                        .compressionFormat(compFormat)
                        .exclusionPatterns(exclusionList)
                        .build();

                BackupResult result = backupService.backupFileSystem(req, null, null);
                stepLog.setOutputPath(result.getDestinationPath());
                stepLog.setFileSizeBytes(result.getFileSizeBytes());
                stepLog.setChecksumSha256(result.getChecksumSha256());
                stepLog.setLogsText(result.isSuccess() ? "File backup successful: " + result.getDestinationPath() : result.getErrorMessage());

                context.setStepOutput(node.getNodeKey(), result.getDestinationPath(), result.getChecksumSha256(), result.getFileSizeBytes());
            }

            case SPLIT_TRANSFER -> {
                String sourceFile = (String) config.get("sourceFilePath");
                if (sourceFile == null || sourceFile.trim().isEmpty() || sourceFile.contains("${")) {
                    sourceFile = context.getLastOutputPath();
                } else {
                    sourceFile = context.resolvePlaceholders(sourceFile);
                }

                long chunkSize = 50 * 1024 * 1024L; // Default 50MB
                if (config.get("chunkSizeMb") != null && !config.get("chunkSizeMb").toString().isBlank()) {
                    try {
                        chunkSize = Long.parseLong(config.get("chunkSizeMb").toString()) * 1024 * 1024L;
                    } catch (Exception ignored) {}
                } else if (config.get("chunkSizeBytes") != null && !config.get("chunkSizeBytes").toString().isBlank()) {
                    try {
                        chunkSize = Long.parseLong(config.get("chunkSizeBytes").toString());
                    } catch (Exception ignored) {}
                }

                TransferRequest req = TransferRequest.builder()
                        .sourceFilePath(sourceFile)
                        .chunkSizeBytes(chunkSize)
                        .remoteDirectory(context.resolvePlaceholders((String) config.getOrDefault("remoteDirectory", "/upload")))
                        .maxRetriesPerChunk(config.get("maxRetries") != null && !config.get("maxRetries").toString().isBlank() ? Integer.parseInt(config.get("maxRetries").toString()) : 3)
                        .executionId(executionId)
                        .build();

                if (config.get("credentialId") != null && !config.get("credentialId").toString().isBlank()) {
                    try {
                        req.setCredentialId(UUID.fromString(config.get("credentialId").toString()));
                    } catch (Exception e) {
                        log.warn("Invalid credentialId UUID in transfer step: {}", config.get("credentialId"));
                    }
                }

                TransferResult result = resilientTransferService.transferFile(req, null, null);
                stepLog.setOutputPath(result.getManifest() != null ? result.getManifest().getOriginalFileName() : sourceFile);
                stepLog.setFileSizeBytes(result.getManifest() != null ? result.getManifest().getOriginalSizeBytes() : 0L);
                stepLog.setChecksumSha256(result.getManifest() != null ? result.getManifest().getOriginalChecksumSha256() : "");
                stepLog.setLogsText("Transferred " + result.getSuccessfulChunks() + "/" + result.getTotalChunks() + " chunks successfully.");

                context.setStepOutput(node.getNodeKey(), sourceFile, stepLog.getChecksumSha256(), stepLog.getFileSizeBytes());
            }

            case EMAIL_ALERT -> {
                String recipient = context.resolvePlaceholders((String) config.getOrDefault("recipient", "admin@example.com"));
                String subject = context.resolvePlaceholders((String) config.getOrDefault("subject", "Custos Pipeline Notification"));

                String sesMsgId = notificationService.sendPipelineSuccessSummary(
                        recipient,
                        node.getNodeLabel(),
                        stepLog.getDurationMs() != null ? stepLog.getDurationMs() : 1000L,
                        context.getLastFileSize() != null ? context.getLastFileSize() : 0L,
                        context.getLastChecksum(),
                        1,
                        context.getLastOutputPath()
                );

                stepLog.setLogsText("Dispatched email alert via AWS SES to: " + recipient + " | SES MessageId: " + sesMsgId);
                stepLog.setAwsSesMessageId(sesMsgId);
                progressBroadcaster.broadcastLog(executionId, node.getNodeLabel(), "SUCCESS", "Dispatched SES Email to: " + recipient);
                log.info("Email Alert step executed: {} -> {} (SES: {})", recipient, subject, sesMsgId);
            }
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse node config override json: {}", json);
            return new HashMap<>();
        }
    }
}
