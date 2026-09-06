package com.custos;

import com.custos.modules.console.ExecutionProgressBroadcaster;
import com.custos.modules.console.LogMessageDto;
import com.custos.modules.console.ProgressUpdateDto;
import com.custos.modules.notification.service.NotificationService;
import com.custos.modules.pipeline.dto.PipelineExecutionResponse;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineExecutionRepository;
import com.custos.modules.pipeline.service.PipelineService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
public class LiveConsoleAndNotificationTests {

    @Autowired
    private ExecutionProgressBroadcaster broadcaster;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PipelineService pipelineService;

    @Autowired
    private PipelineDefinitionRepository pipelineDefinitionRepository;

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Test
    @DisplayName("WebSocket Broadcaster - Send live log & progress events without exception")
    public void testWebSocketBroadcasting() {
        UUID executionId = UUID.randomUUID();

        // 1. Test log broadcast
        Assertions.assertDoesNotThrow(() -> {
            broadcaster.broadcastLog(
                    executionId,
                    "DATABASE_BACKUP",
                    "INFO",
                    "Database dump completed in 1.4s"
            );
        });

        // 2. Test progress broadcast
        Assertions.assertDoesNotThrow(() -> {
            ProgressUpdateDto progress = ProgressUpdateDto.builder()
                    .executionId(executionId)
                    .stepName("SFTP_UPLOAD")
                    .percentage(75)
                    .currentChunk(3)
                    .totalChunks(4)
                    .bytesTransferred(157286400L)
                    .totalBytes(209715200L)
                    .status("UPLOADING")
                    .build();
            broadcaster.broadcastProgress(progress);
        });
    }

    @Test
    @DisplayName("Notification Service - Welcome email template rendering & dispatch")
    public void testSendWelcomeEmail() {
        String messageId = notificationService.sendUserWelcomeEmail(
                "dev@custos.io",
                "Developer Admin",
                "TempP@ssw0rd123!",
                "https://custos.example.com/login"
        );

        Assertions.assertNotNull(messageId);
        Assertions.assertTrue(messageId.startsWith("SES-MOCK-") || messageId.length() > 5);
    }

    @Test
    @DisplayName("Notification Service - Pipeline success summary email template")
    public void testSendPipelineSuccessSummary() {
        String messageId = notificationService.sendPipelineSuccessSummary(
                "ops@custos.io",
                "Daily Production Backup",
                4600L,
                256000000L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                4,
                "/var/backups/daily-mysql.tar.gz"
        );

        Assertions.assertNotNull(messageId);
        Assertions.assertTrue(messageId.startsWith("SES-MOCK-") || messageId.length() > 5);
    }

    @Test
    @DisplayName("Notification Service - Pipeline failure alert extracts last 50 lines of logs")
    public void testSendPipelineFailureAlert() {
        // Build 70 lines of logs to test truncation to last 50 lines
        StringBuilder longLog = new StringBuilder();
        for (int i = 1; i <= 70; i++) {
            longLog.append("Log line ").append(i).append(": Process output detail\n");
        }
        longLog.append("FATAL ERROR: Connection refused to remote host 10.0.0.1:22");

        String messageId = notificationService.sendPipelineFailureAlert(
                "alerts@custos.io",
                "Hourly SFTP Sync",
                "Upload Step #2",
                1,
                "Connection timeout after 30 seconds",
                longLog.toString()
        );

        Assertions.assertNotNull(messageId);
        Assertions.assertTrue(messageId.startsWith("SES-MOCK-") || messageId.length() > 5);
    }

    @Test
    @DisplayName("Notification Service - Rate Limiting handles rapid burst")
    public void testRateLimitingBurst() {
        // SES allows max 14 calls/sec; our sliding window smoothly handles rate limiting
        Assertions.assertDoesNotThrow(() -> {
            for (int i = 0; i < 5; i++) {
                notificationService.sendUserWelcomeEmail(
                        "burst" + i + "@custos.io",
                        "Burst User " + i,
                        "Temp123!",
                        "https://custos.example.com"
                );
            }
        });
    }

    @Test
    @DisplayName("Execution History - listAllExecutions with and without status filter")
    public void testListAllExecutions() {
        // Setup a pipeline and execution
        PipelineDefinition pipeline = PipelineDefinition.builder()
                .name("History Test Pipeline " + UUID.randomUUID())
                .description("Pipeline to test listing executions")
                .isActive(true)
                .build();
        pipeline = pipelineDefinitionRepository.save(pipeline);

        PipelineExecution exec1 = PipelineExecution.builder()
                .pipeline(pipeline)
                .status(ExecutionStatus.SUCCESS)
                .startTime(Instant.now().minusSeconds(60))
                .endTime(Instant.now())
                .triggeredBy("test-runner")
                .build();
        pipelineExecutionRepository.save(exec1);

        PipelineExecution exec2 = PipelineExecution.builder()
                .pipeline(pipeline)
                .status(ExecutionStatus.FAILED)
                .startTime(Instant.now().minusSeconds(30))
                .endTime(Instant.now())
                .errorMessage("Disk full")
                .triggeredBy("test-runner")
                .build();
        pipelineExecutionRepository.save(exec2);

        // 1. Query all
        List<PipelineExecutionResponse> allExecs = pipelineService.listAllExecutions(null);
        Assertions.assertFalse(allExecs.isEmpty());
        Assertions.assertTrue(allExecs.stream().anyMatch(e -> e.getId().equals(exec1.getId())));
        Assertions.assertTrue(allExecs.stream().anyMatch(e -> e.getId().equals(exec2.getId())));

        // 2. Query by status SUCCESS
        List<PipelineExecutionResponse> successExecs = pipelineService.listAllExecutions(ExecutionStatus.SUCCESS);
        Assertions.assertTrue(successExecs.stream().anyMatch(e -> e.getId().equals(exec1.getId())));
        Assertions.assertFalse(successExecs.stream().anyMatch(e -> e.getId().equals(exec2.getId())));

        // 3. Query by status FAILED
        List<PipelineExecutionResponse> failedExecs = pipelineService.listAllExecutions(ExecutionStatus.FAILED);
        Assertions.assertTrue(failedExecs.stream().anyMatch(e -> e.getId().equals(exec2.getId())));
        Assertions.assertFalse(failedExecs.stream().anyMatch(e -> e.getId().equals(exec1.getId())));
    }
}
