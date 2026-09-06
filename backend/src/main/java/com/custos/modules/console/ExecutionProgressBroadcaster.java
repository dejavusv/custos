package com.custos.modules.console;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionProgressBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastLog(UUID executionId, String stepName, String level, String message) {
        if (executionId == null) return;
        try {
            LogMessageDto logMsg = LogMessageDto.builder()
                    .executionId(executionId)
                    .stepName(stepName)
                    .level(level != null ? level.toUpperCase() : "INFO")
                    .message(message)
                    .timestamp(Instant.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/pipeline/" + executionId + "/logs", logMsg);
        } catch (Exception e) {
            log.warn("Failed to broadcast log over WebSocket for execution {}: {}", executionId, e.getMessage());
        }
    }

    public void broadcastProgress(ProgressUpdateDto progress) {
        if (progress == null || progress.getExecutionId() == null) return;
        try {
            messagingTemplate.convertAndSend(
                    "/topic/pipeline/" + progress.getExecutionId() + "/progress",
                    progress
            );
        } catch (Exception e) {
            log.warn("Failed to broadcast progress over WebSocket for execution {}: {}", progress.getExecutionId(), e.getMessage());
        }
    }
}
