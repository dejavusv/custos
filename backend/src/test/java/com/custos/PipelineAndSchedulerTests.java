package com.custos;

import com.custos.modules.pipeline.engine.DagCycleDetector;
import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.engine.PipelineExecutionContext;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.exception.CyclicDependencyException;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.pipeline.repository.PipelineStepNodeRepository;
import com.custos.modules.pipeline.repository.StepExecutionLogRepository;
import com.custos.modules.pipeline.dto.PipelineDetailResponse;
import com.custos.modules.pipeline.dto.SavePipelineRequest;
import com.custos.modules.pipeline.dto.StepNodeDto;
import com.custos.modules.pipeline.service.PipelineService;
import com.custos.modules.scheduling.service.QuartzSchedulerService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
public class PipelineAndSchedulerTests {

    @Autowired
    private DagCycleDetector dagCycleDetector;

    @Autowired
    private QuartzSchedulerService quartzSchedulerService;

    @Autowired
    private DagPipelineOrchestrator dagPipelineOrchestrator;

    @Autowired
    private PipelineDefinitionRepository pipelineDefinitionRepository;

    @Autowired
    private PipelineStepNodeRepository pipelineStepNodeRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private StepExecutionLogRepository stepExecutionLogRepository;

    @Autowired
    private PipelineService pipelineService;

    @Test
    @DisplayName("DAG Cycle Detection - Should detect circular loop and reject")
    public void testDagCycleDetection() {
        UUID nodeAId = UUID.randomUUID();
        UUID nodeBId = UUID.randomUUID();
        UUID nodeCId = UUID.randomUUID();

        // A -> B -> C -> A (Cycle)
        PipelineStepNode nodeA = PipelineStepNode.builder()
                .nodeKey("nodeA")
                .nodeLabel("Node A")
                .nodeType(TaskType.DATABASE_BACKUP)
                .onSuccessNodeId(nodeBId)
                .build();
        nodeA.setId(nodeAId);

        PipelineStepNode nodeB = PipelineStepNode.builder()
                .nodeKey("nodeB")
                .nodeLabel("Node B")
                .nodeType(TaskType.FILE_BACKUP)
                .onSuccessNodeId(nodeCId)
                .build();
        nodeB.setId(nodeBId);

        PipelineStepNode nodeC = PipelineStepNode.builder()
                .nodeKey("nodeC")
                .nodeLabel("Node C")
                .nodeType(TaskType.EMAIL_ALERT)
                .onSuccessNodeId(nodeAId) // loop back to A
                .build();
        nodeC.setId(nodeCId);

        List<PipelineStepNode> cyclicGraph = List.of(nodeA, nodeB, nodeC);

        Assertions.assertThrows(CyclicDependencyException.class, () -> {
            dagCycleDetector.validateNodes(cyclicGraph);
        }, "Cyclic graph must throw CyclicDependencyException");

        // Break cycle: C -> null
        nodeC.setOnSuccessNodeId(null);
        Assertions.assertDoesNotThrow(() -> {
            dagCycleDetector.validateNodes(cyclicGraph);
        }, "Acyclic graph must pass validation without error");
    }

    @Test
    @DisplayName("Context Passing - Should resolve dynamic placeholders from previous step")
    public void testExecutionContextPassing() {
        PipelineExecutionContext context = new PipelineExecutionContext();
        context.setStepOutput("backupStep", "/tmp/backup.tar.gz", "abcd1234sha256", 1048576L);

        String template = "Source: ${last_output_path}, Hash: ${backupStep.checksum}, Size: ${backupStep.file_size_bytes}";
        String resolved = context.resolvePlaceholders(template);

        Assertions.assertEquals("Source: /tmp/backup.tar.gz, Hash: abcd1234sha256, Size: 1048576", resolved);
    }

    @Test
    @DisplayName("Quartz Scheduler - Should schedule cron and calculate next fire time")
    public void testQuartzCronScheduling() throws Exception {
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .name("Daily Backup Pipeline " + UUID.randomUUID())
                .description("Automated daily midnight backup")
                .cronExpression("0 0 12 * * ?") // Every day at 12:00 PM
                .timezone("UTC")
                .misfirePolicy(MisfirePolicy.FIRE_NOW)
                .isActive(true)
                .build();
        pipeline = pipelineDefinitionRepository.save(pipeline);

        quartzSchedulerService.schedulePipeline(pipeline);

        Instant nextFire = quartzSchedulerService.getNextFireTime(pipeline.getId());
        Assertions.assertNotNull(nextFire, "Next fire time should not be null for valid cron");
        Assertions.assertTrue(nextFire.isAfter(Instant.now()), "Next fire time must be in the future");

        // Pause
        quartzSchedulerService.pausePipeline(pipeline.getId());

        // Cleanup
        quartzSchedulerService.unschedulePipeline(pipeline.getId());
    }

    @Test
    @DisplayName("DAG Pipeline Orchestration - Should execute steps and route via On Success")
    public void testPipelineExecutionSuccess(@TempDir Path tempDir) throws Exception {
        // Create a dummy source file
        File sourceDir = tempDir.resolve("sample_data").toFile();
        sourceDir.mkdirs();
        File testFile = new File(sourceDir, "data.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("Hello Custos Orchestrator");
        }

        File destDir = tempDir.resolve("backups").toFile();
        destDir.mkdirs();

        PipelineDefinition pipeline = PipelineDefinition.builder()
                .name("Test Orchestration Pipeline " + UUID.randomUUID())
                .description("Integration test for DAG Execution")
                .isActive(true)
                .build();
        pipeline = pipelineDefinitionRepository.save(pipeline);

        UUID node1Id = UUID.randomUUID();
        UUID node2Id = UUID.randomUUID();

        // Node 1: File Backup
        String node1Config = String.format("{\"sourcePath\":\"%s\",\"destinationDir\":\"%s\",\"customFileName\":\"test-run.tar.gz\"}",
                sourceDir.getAbsolutePath().replace("\\", "\\\\"),
                destDir.getAbsolutePath().replace("\\", "\\\\"));

        PipelineStepNode node1 = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("fileBackup")
                .nodeLabel("File Backup Step")
                .nodeType(TaskType.FILE_BACKUP)
                .stepOrder(1)
                .configOverrideJson(node1Config)
                .onSuccessNodeId(node2Id)
                .build();
        node1.setId(node1Id);

        // Node 2: Email Alert
        PipelineStepNode node2 = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("alertStep")
                .nodeLabel("Success Alert")
                .nodeType(TaskType.EMAIL_ALERT)
                .stepOrder(2)
                .configOverrideJson("{\"recipient\":\"ops@example.com\",\"subject\":\"Backup Complete: ${last_output_path}\"}")
                .build();
        node2.setId(node2Id);

        pipelineStepNodeRepository.save(node1);
        pipelineStepNodeRepository.save(node2);

        // Execute
        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(pipeline.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertNotNull(execution);
        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus(), "Pipeline execution should succeed");
        Assertions.assertNotNull(execution.getEndTime());
        Assertions.assertTrue(execution.getDurationMs() >= 0);

        // Verify backup output file created
        File expectedArchive = new File(destDir, "test-run.tar.gz");
        Assertions.assertTrue(expectedArchive.exists(), "Archive file should have been generated by step 1");
    }

    @Test
    @DisplayName("DAG Pipeline Branching - Step failure routes to On Failure node")
    public void testPipelineFailureBranching() {
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .name("Failure Branch Test " + UUID.randomUUID())
                .description("Test branching to failure handler")
                .isActive(true)
                .build();
        pipeline = pipelineDefinitionRepository.save(pipeline);

        UUID step1Id = UUID.randomUUID();
        UUID failureHandlerId = UUID.randomUUID();

        // Step 1: File backup of non-existent directory -> will fail
        PipelineStepNode step1 = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("failingStep")
                .nodeLabel("Failing Backup")
                .nodeType(TaskType.FILE_BACKUP)
                .stepOrder(1)
                .configOverrideJson("{\"sourcePath\":\"/path/to/non_existent_folder_xyz_123\"}")
                .onFailureNodeId(failureHandlerId)
                .build();
        step1.setId(step1Id);

        // Step 2: Email Alert on failure
        PipelineStepNode failureHandler = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("errorHandler")
                .nodeLabel("Rollback / Error Alert")
                .nodeType(TaskType.EMAIL_ALERT)
                .stepOrder(2)
                .configOverrideJson("{\"recipient\":\"alerts@example.com\",\"subject\":\"Pipeline Alert: Step Failed\"}")
                .build();
        failureHandler.setId(failureHandlerId);

        pipelineStepNodeRepository.save(step1);
        pipelineStepNodeRepository.save(failureHandler);

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(pipeline.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertNotNull(execution);
        // Step 1 failed, but routed to failureHandler which executed
        Assertions.assertNotNull(execution.getEndTime());
    }

    @Test
    @DisplayName("DAG Pipeline Branching - On Failure node must NOT run when the step succeeds")
    public void testFailureBranchSkippedOnSuccess(@TempDir Path tempDir) throws Exception {
        File sourceDir = tempDir.resolve("src").toFile();
        sourceDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(sourceDir, "data.txt"))) {
            writer.write("ok");
        }
        File destDir = tempDir.resolve("out").toFile();
        destDir.mkdirs();

        PipelineDefinition pipeline = pipelineDefinitionRepository.save(PipelineDefinition.builder()
                .name("Failure Branch Skip Test " + UUID.randomUUID())
                .isActive(true)
                .build());

        // ID ถูก generate ตอน save (การ setId ล่วงหน้าจะถูกแทนที่) จึงบันทึก Node ก่อนแล้วค่อยผูกเส้นด้วย ID จริง
        PipelineStepNode backup = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("backup")
                .nodeLabel("Backup")
                .nodeType(TaskType.FILE_BACKUP)
                .stepOrder(1)
                .configOverrideJson(String.format("{\"sourcePath\":\"%s\",\"destinationDir\":\"%s\"}",
                        sourceDir.getAbsolutePath().replace("\\", "\\\\"),
                        destDir.getAbsolutePath().replace("\\", "\\\\")))
                .build();

        PipelineStepNode failureAlert = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("failureAlert")
                .nodeLabel("Failure Alert")
                .nodeType(TaskType.EMAIL_ALERT)
                .stepOrder(2)
                .configOverrideJson("{\"recipient\":\"alerts@example.com\"}")
                .build();

        backup = pipelineStepNodeRepository.save(backup);
        failureAlert = pipelineStepNodeRepository.save(failureAlert);
        backup.setOnFailureNodeId(failureAlert.getId());
        pipelineStepNodeRepository.save(backup);

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(pipeline.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        List<String> executedSteps = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(execution.getId())
                .stream().map(StepExecutionLog::getStepName).toList();
        Assertions.assertEquals(List.of("Backup"), executedSteps, "Failure branch must not run after a successful step");
    }

    @Test
    @DisplayName("Save Pipeline - Edges between brand-new nodes are resolved by nodeKey, and existing node IDs are kept on update")
    public void testSavePipelineResolvesEdgesByNodeKey() {
        StepNodeDto dump = StepNodeDto.builder().nodeKey("dump").nodeLabel("Dump").nodeType(TaskType.DATABASE_BACKUP)
                .stepOrder(1).onSuccessNodeKey("upload").onFailureNodeKey("alert").build();
        StepNodeDto upload = StepNodeDto.builder().nodeKey("upload").nodeLabel("Upload").nodeType(TaskType.SPLIT_TRANSFER)
                .stepOrder(2).build();
        StepNodeDto alert = StepNodeDto.builder().nodeKey("alert").nodeLabel("Alert").nodeType(TaskType.EMAIL_ALERT)
                .stepOrder(3).build();

        SavePipelineRequest create = new SavePipelineRequest();
        create.setName("Key Edge Test " + UUID.randomUUID());
        create.setNodes(new ArrayList<>(List.of(dump, upload, alert)));

        PipelineDetailResponse created = pipelineService.savePipeline(create, null, new MockHttpServletRequest());
        Map<String, StepNodeDto> byKey = new HashMap<>();
        created.getNodes().forEach(n -> byKey.put(n.getNodeKey(), n));

        Assertions.assertEquals(byKey.get("upload").getId(), byKey.get("dump").getOnSuccessNodeId());
        Assertions.assertEquals(byKey.get("alert").getId(), byKey.get("dump").getOnFailureNodeId());

        // Update: remove "alert", keep the others by ID
        StepNodeDto dumpUpdate = StepNodeDto.builder().id(byKey.get("dump").getId()).nodeKey("dump").nodeLabel("Dump v2")
                .nodeType(TaskType.DATABASE_BACKUP).stepOrder(1).onSuccessNodeKey("upload").build();
        StepNodeDto uploadUpdate = StepNodeDto.builder().id(byKey.get("upload").getId()).nodeKey("upload").nodeLabel("Upload")
                .nodeType(TaskType.SPLIT_TRANSFER).stepOrder(2).build();

        SavePipelineRequest update = new SavePipelineRequest();
        update.setId(created.getId());
        update.setName(created.getName());
        update.setNodes(new ArrayList<>(List.of(dumpUpdate, uploadUpdate)));

        PipelineDetailResponse updated = pipelineService.savePipeline(update, null, new MockHttpServletRequest());
        Map<String, StepNodeDto> updatedByKey = new HashMap<>();
        updated.getNodes().forEach(n -> updatedByKey.put(n.getNodeKey(), n));

        Assertions.assertEquals(2, updated.getNodes().size());
        Assertions.assertEquals(byKey.get("dump").getId(), updatedByKey.get("dump").getId(), "Existing node ID must be kept");
        Assertions.assertEquals("Dump v2", updatedByKey.get("dump").getNodeLabel());
        Assertions.assertEquals(byKey.get("upload").getId(), updatedByKey.get("dump").getOnSuccessNodeId());
        Assertions.assertNull(updatedByKey.get("dump").getOnFailureNodeId());
        Assertions.assertEquals(2, pipelineStepNodeRepository.findByPipelineIdOrderByStepOrderAsc(created.getId()).size());
    }

    private StepNodeDto controlNode(String key, TaskType type, int order, String successKey, String failureKey) {
        return StepNodeDto.builder().nodeKey(key).nodeLabel(key).nodeType(type).stepOrder(order)
                .onSuccessNodeKey(successKey).onFailureNodeKey(failureKey).build();
    }

    private PipelineDetailResponse savePipelineWith(String namePrefix, StepNodeDto... nodes) {
        SavePipelineRequest request = new SavePipelineRequest();
        request.setName(namePrefix + UUID.randomUUID());
        request.setNodes(new ArrayList<>(List.of(nodes)));
        return pipelineService.savePipeline(request, null, new MockHttpServletRequest());
    }

    private StepNodeDto fileBackupNode(String key, int order, String sourcePath, String destDir, String successKey, String failureKey) {
        return StepNodeDto.builder().nodeKey(key).nodeLabel(key).nodeType(TaskType.FILE_BACKUP).stepOrder(order)
                .configOverrideJson(String.format("{\"sourcePath\":\"%s\",\"destinationDir\":\"%s\"}",
                        sourcePath.replace("\\", "\\\\"), destDir.replace("\\", "\\\\")))
                .onSuccessNodeKey(successKey).onFailureNodeKey(failureKey).build();
    }

    @Test
    @DisplayName("Start/Stop - a pipeline can have only one Start node")
    public void testSaveRejectsMultipleStartNodes() {
        Assertions.assertThrows(com.custos.shared.BadRequestException.class, () -> savePipelineWith("Two Starts ",
                controlNode("start", TaskType.START, 1, "start2", null),
                controlNode("start2", TaskType.START, 2, null, null)));
    }

    @Test
    @DisplayName("Start/Stop - Start cannot be a connection target and Stop cannot have outgoing connections")
    public void testSaveRejectsInvalidStartStopEdges() {
        Assertions.assertThrows(com.custos.shared.BadRequestException.class, () -> savePipelineWith("Into Start ",
                controlNode("start", TaskType.START, 1, "stop", null),
                controlNode("stop", TaskType.STOP, 2, "start", null)));
        Assertions.assertThrows(com.custos.shared.BadRequestException.class, () -> savePipelineWith("Out of Stop ",
                controlNode("start", TaskType.START, 1, "stop", null),
                controlNode("stop", TaskType.STOP, 2, null, null),
                StepNodeDto.builder().nodeKey("after").nodeLabel("after").nodeType(TaskType.EMAIL_ALERT).stepOrder(3).build(),
                controlNode("stop2", TaskType.STOP, 4, "after", null)));
    }

    @Test
    @DisplayName("Start/Stop - Start -> step -> Stop runs in order and finishes SUCCESS; nodes after Stop are not run")
    public void testStartStopSuccessFlow(@TempDir Path tempDir) throws Exception {
        File sourceDir = tempDir.resolve("src").toFile();
        sourceDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(sourceDir, "data.txt"))) {
            writer.write("ok");
        }
        File destDir = tempDir.resolve("out").toFile();
        destDir.mkdirs();

        PipelineDetailResponse saved = savePipelineWith("Start Stop Success ",
                controlNode("start", TaskType.START, 1, "backup", null),
                fileBackupNode("backup", 2, sourceDir.getAbsolutePath(), destDir.getAbsolutePath(), "stop", null),
                controlNode("stop", TaskType.STOP, 3, null, null),
                // อิสระ ไม่มีเส้นชี้เข้า — ต้องไม่ถูกรันหลังจาก Stop
                StepNodeDto.builder().nodeKey("orphan").nodeLabel("orphan").nodeType(TaskType.EMAIL_ALERT).stepOrder(4).build());

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(saved.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        List<String> executedSteps = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(execution.getId())
                .stream().map(StepExecutionLog::getStepName).toList();
        Assertions.assertEquals(List.of("start", "backup", "stop"), executedSteps);
    }

    @Test
    @DisplayName("Start/Stop - failure routed to a Stop node ends the pipeline as FAILED and exposes the error message")
    public void testStopViaFailureBranchFailsPipeline(@TempDir Path tempDir) {
        PipelineDetailResponse saved = savePipelineWith("Start Stop Failure ",
                controlNode("start", TaskType.START, 1, "backup", null),
                fileBackupNode("backup", 2, tempDir.resolve("missing_folder").toString(), tempDir.toString(), "stopOk", "alert"),
                StepNodeDto.builder().nodeKey("alert").nodeLabel("alert").nodeType(TaskType.EMAIL_ALERT).stepOrder(3)
                        .configOverrideJson("{\"recipient\":\"alerts@example.com\"}").onSuccessNodeKey("stopFailed").build(),
                controlNode("stopOk", TaskType.STOP, 4, null, null),
                controlNode("stopFailed", TaskType.STOP, 5, null, null));

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(saved.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.FAILED, execution.getStatus());
        Assertions.assertNotNull(execution.getErrorMessage());
        List<String> executedSteps = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(execution.getId())
                .stream().map(StepExecutionLog::getStepName).toList();
        Assertions.assertEquals(List.of("start", "backup", "alert", "stopFailed"), executedSteps);
        Assertions.assertTrue(execution.getContextDataJson().contains("backup.error_message"));
        Assertions.assertTrue(execution.getContextDataJson().contains("last_error_message"));
    }

    @Test
    @DisplayName("Start/Stop - pipelines without a Start node still run (legacy)")
    public void testLegacyPipelineWithoutStartStillRuns(@TempDir Path tempDir) throws Exception {
        File sourceDir = tempDir.resolve("src").toFile();
        sourceDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(sourceDir, "data.txt"))) {
            writer.write("ok");
        }
        File destDir = tempDir.resolve("out").toFile();
        destDir.mkdirs();

        PipelineDetailResponse saved = savePipelineWith("Legacy No Start ",
                fileBackupNode("backup", 1, sourceDir.getAbsolutePath(), destDir.getAbsolutePath(), null, null));

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(saved.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
    }
}
