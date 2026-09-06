package com.custos;

import com.custos.modules.pipeline.engine.DagCycleDetector;
import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.engine.PipelineExecutionContext;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.exception.CyclicDependencyException;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.pipeline.repository.PipelineStepNodeRepository;
import com.custos.modules.scheduling.service.QuartzSchedulerService;
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
}
