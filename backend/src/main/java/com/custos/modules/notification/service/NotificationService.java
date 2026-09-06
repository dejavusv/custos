package com.custos.modules.notification.service;

import com.custos.config.AwsSesConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final AwsSesConfig awsSesConfig;
    private final SpringTemplateEngine templateEngine;

    @Autowired(required = false)
    private SesClient sesClient;

    // In-memory rate limiting timestamps queue
    private final Queue<Long> sendTimestamps = new ArrayDeque<>();

    public synchronized void rateLimit() {
        int maxRate = awsSesConfig.getRateLimitPerSecond() > 0 ? awsSesConfig.getRateLimitPerSecond() : 14;
        long now = System.currentTimeMillis();

        // Evict timestamps older than 1000ms
        while (!sendTimestamps.isEmpty() && now - sendTimestamps.peek() > 1000) {
            sendTimestamps.poll();
        }

        if (sendTimestamps.size() >= maxRate) {
            long oldestInWindow = sendTimestamps.peek();
            long sleepTime = 1000 - (now - oldestInWindow) + 10;
            if (sleepTime > 0) {
                try {
                    log.debug("SES Rate limit threshold reached ({}/sec), throttling for {}ms", maxRate, sleepTime);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        sendTimestamps.add(System.currentTimeMillis());
    }

    public String sendEmail(String recipient, String subject, String htmlBody) {
        if (recipient == null || recipient.trim().isEmpty()) {
            throw new IllegalArgumentException("Recipient email cannot be empty");
        }

        rateLimit();

        if (sesClient != null && awsSesConfig.isEnabled()) {
            try {
                SendEmailRequest request = SendEmailRequest.builder()
                        .destination(Destination.builder().toAddresses(recipient.trim()).build())
                        .source(awsSesConfig.getSenderEmail())
                        .message(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build();

                SendEmailResponse response = sesClient.sendEmail(request);
                log.info("Sent email via AWS SES to '{}'. MessageId: {}", recipient, response.messageId());
                return response.messageId();
            } catch (Exception e) {
                log.error("Failed to send email via AWS SES to '{}': {}", recipient, e.getMessage());
                throw new RuntimeException("AWS SES Send Failure: " + e.getMessage(), e);
            }
        } else {
            String mockMessageId = "SES-MOCK-" + UUID.randomUUID();
            log.info("[MOCK AWS SES] Dispatched email to '{}' | Subject: '{}' | MessageId: {}", recipient, subject, mockMessageId);
            return mockMessageId;
        }
    }

    public String sendPipelineSuccessSummary(
            String recipient,
            String pipelineName,
            long durationMs,
            long fileSizeBytes,
            String checksumSha256,
            int chunkCount,
            String outputPath
    ) {
        Context context = new Context();
        context.setVariable("pipelineName", pipelineName != null ? pipelineName : "Custos Pipeline");
        context.setVariable("duration", String.format("%.2fs", durationMs / 1000.0));
        context.setVariable("fileSizeFormatted", String.format("%.2f MB", fileSizeBytes / (1024.0 * 1024.0)));
        context.setVariable("checksumSha256", checksumSha256 != null ? checksumSha256 : "N/A");
        context.setVariable("chunkCount", chunkCount > 0 ? (chunkCount + " chunks") : "Single File");
        context.setVariable("outputPath", outputPath != null ? outputPath : "N/A");

        String html = templateEngine.process("email/pipeline-success", context);
        String subject = "\u2705 [Custos] Pipeline Succeeded: " + pipelineName;
        return sendEmail(recipient, subject, html);
    }

    public String sendPipelineFailureAlert(
            String recipient,
            String pipelineName,
            String failedStepName,
            Integer exitCode,
            String errorMessage,
            String fullLogs
    ) {
        Context context = new Context();
        context.setVariable("pipelineName", pipelineName != null ? pipelineName : "Custos Pipeline");
        context.setVariable("failedStepName", failedStepName != null ? failedStepName : "Unknown Step");
        context.setVariable("exitCode", exitCode);
        context.setVariable("errorMessage", errorMessage != null ? errorMessage : "Process aborted or encountered an error");
        context.setVariable("last50LinesLog", extractLast50Lines(fullLogs));

        String html = templateEngine.process("email/pipeline-failure", context);
        String subject = "\u26A0\uFE0F [Custos ALERT] Pipeline Failed: " + pipelineName;
        return sendEmail(recipient, subject, html);
    }

    public String sendUserWelcomeEmail(String recipient, String username, String temporaryPassword, String resetToken) {
        Context context = new Context();
        context.setVariable("username", username);
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("resetToken", resetToken);
        context.setVariable("message", "Welcome to Custos Platform. Your account is ready for operation.");

        String html = templateEngine.process("email/user-welcome", context);
        String subject = "[Custos] Welcome to Server Automation Platform";
        return sendEmail(recipient, subject, html);
    }

    public String extractLast50Lines(String fullLogs) {
        if (fullLogs == null || fullLogs.trim().isEmpty()) {
            return "No execution logs available.";
        }

        String[] lines = fullLogs.split("\\r?\\n");
        if (lines.length <= 50) {
            return fullLogs;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - 50; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString().trim();
    }
}
