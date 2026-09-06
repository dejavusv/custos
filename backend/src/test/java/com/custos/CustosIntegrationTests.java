package com.custos;

import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.pipeline.repository.PipelineStepNodeRepository;
import com.custos.modules.transfer.engine.ChunkSplitterEngine;
import com.custos.modules.transfer.model.ChunkMetadata;
import com.custos.modules.transfer.model.TransferManifest;
import com.custos.modules.transfer.service.ChecksumService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
public class CustosIntegrationTests {

    @Autowired
    private DagPipelineOrchestrator dagPipelineOrchestrator;

    @Autowired
    private PipelineDefinitionRepository pipelineDefinitionRepository;

    @Autowired
    private PipelineStepNodeRepository pipelineStepNodeRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    private com.custos.modules.pipeline.repository.StepExecutionLogRepository stepExecutionLogRepository;

    @Autowired
    private ChunkSplitterEngine chunkSplitterEngine;

    @Autowired
    private ChecksumService checksumService;

    @Test
    @DisplayName("TASK-801 Integration: Full End-to-End Pipeline Execution (Backup -> Email Alert)")
    public void testFullPipelineEndToEndExecution(@TempDir Path tempDir) throws IOException {
        // 1. Create dummy files to back up
        Path sourceDir = tempDir.resolve("project-data");
        Files.createDirectories(sourceDir);
        for (int i = 1; i <= 5; i++) {
            File doc = sourceDir.resolve("doc_" + i + ".txt").toFile();
            try (FileWriter writer = new FileWriter(doc)) {
                writer.write("Custos Platform Integration Payload Data " + i + "\n" + UUID.randomUUID());
            }
        }

        File destDir = tempDir.resolve("backups").toFile();
        destDir.mkdirs();

        // 2. Define Pipeline
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .name("E2E Integration Test Pipeline " + UUID.randomUUID())
                .description("End-to-End full pipeline validation")
                .isActive(true)
                .build();
        pipeline = pipelineDefinitionRepository.save(pipeline);

        // 3. Define Step Nodes: Node 1 (File Backup) -> Node 2 (Email Alert)
        UUID node1Id = UUID.randomUUID();
        UUID node2Id = UUID.randomUUID();

        String node1Config = String.format("{\"sourcePath\":\"%s\",\"destinationDir\":\"%s\",\"customFileName\":\"integration-run.tar.gz\"}",
                sourceDir.toFile().getAbsolutePath().replace("\\", "\\\\"),
                destDir.getAbsolutePath().replace("\\", "\\\\"));

        PipelineStepNode node1 = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("fileBackup")
                .nodeLabel("Archive File Backup")
                .nodeType(TaskType.FILE_BACKUP)
                .stepOrder(1)
                .onSuccessNodeId(node2Id)
                .configOverrideJson(node1Config)
                .build();
        node1.setId(node1Id);

        PipelineStepNode node2 = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("emailAlert")
                .nodeLabel("SES Success Alert")
                .nodeType(TaskType.EMAIL_ALERT)
                .stepOrder(2)
                .configOverrideJson("{\"recipient\":\"ops@custos.io\",\"subject\":\"Integration Complete: ${last_output_path}\"}")
                .build();
        node2.setId(node2Id);

        pipelineStepNodeRepository.save(node1);
        pipelineStepNodeRepository.save(node2);

        // 4. Run Orchestrator
        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(
                pipeline.getId(),
                "INTEGRATION_RUNNER",
                TriggerType.MANUAL
        );

        // 5. Assertions
        Assertions.assertNotNull(execution);
        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus(), "Pipeline should finish with SUCCESS");
        Assertions.assertNotNull(execution.getStartTime());
        Assertions.assertNotNull(execution.getEndTime());
        Assertions.assertTrue(execution.getDurationMs() >= 0);

        // Verify backup output file created
        File expectedArchive = new File(destDir, "integration-run.tar.gz");
        Assertions.assertTrue(expectedArchive.exists(), "Archive file should have been generated by step 1");

        List<StepExecutionLog> logs = stepExecutionLogRepository.findByExecutionIdOrderByStartTimeAsc(execution.getId());
        Assertions.assertFalse(logs.isEmpty(), "Step execution logs should be recorded");
        Assertions.assertTrue(logs.stream().anyMatch(l -> l.getStepName().equals("Archive File Backup")));
        Assertions.assertTrue(logs.stream().anyMatch(l -> l.getStepName().equals("SES Success Alert")));
    }

    @Test
    @DisplayName("TASK-801 Integration: Chunk Split, SHA-256 Checksum, and Merge Roundtrip")
    public void testChunkSplitChecksumMergeRoundtrip(@TempDir Path tempDir) throws IOException {
        // 1. Create a 200KB payload file
        File originalFile = tempDir.resolve("integration_payload.bin").toFile();
        byte[] payload = new byte[200 * 1024];
        new Random(42).nextBytes(payload);
        Files.write(originalFile.toPath(), payload);

        String originalSha256 = checksumService.calculateSha256(originalFile);
        Assertions.assertNotNull(originalSha256);

        // 2. Split into 64KB chunks
        long chunkSizeBytes = 64 * 1024L;
        File chunkDir = tempDir.resolve("chunks").toFile();
        chunkDir.mkdirs();

        TransferManifest manifest = chunkSplitterEngine.splitFile(originalFile, chunkDir, chunkSizeBytes);
        Assertions.assertNotNull(manifest);
        Assertions.assertEquals(4, manifest.getTotalChunks()); // 200KB / 64KB = 3 full + 1 remainder = 4 chunks
        Assertions.assertEquals(originalSha256, manifest.getOriginalChecksumSha256());

        // 3. Verify each chunk's individual checksum
        List<File> chunkFiles = new ArrayList<>();
        for (ChunkMetadata chunk : manifest.getChunks()) {
            File chunkFile = new File(chunk.getFilePath());
            Assertions.assertTrue(chunkFile.exists());
            chunkFiles.add(chunkFile);
            String chunkSha256 = checksumService.calculateSha256(chunkFile);
            Assertions.assertEquals(chunk.getChecksumSha256(), chunkSha256);
            Assertions.assertTrue(checksumService.verifyChecksum(chunkFile, chunk.getChecksumSha256()));
        }

        // 4. Merge back to restored file
        File restoredFile = tempDir.resolve("restored_payload.bin").toFile();
        File mergedResult = chunkSplitterEngine.mergeChunks(chunkFiles, restoredFile);
        Assertions.assertTrue(mergedResult.exists());
        Assertions.assertEquals(originalFile.length(), mergedResult.length());

        // 5. Verify restored file SHA-256 matches original exactly
        String restoredSha256 = checksumService.calculateSha256(mergedResult);
        Assertions.assertEquals(originalSha256, restoredSha256);
    }
}
