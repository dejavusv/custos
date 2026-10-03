package com.custos;

import com.custos.modules.externalnotify.client.OutboundUrlValidator;
import com.custos.modules.externalnotify.dto.ExternalNotifySendResult;
import com.custos.modules.externalnotify.dto.ExternalNotifyTaskDto;
import com.custos.modules.externalnotify.service.ExternalNotifyService;
import com.custos.modules.pipeline.engine.DagPipelineOrchestrator;
import com.custos.modules.pipeline.entity.*;
import com.custos.modules.pipeline.repository.PipelineDefinitionRepository;
import com.custos.modules.pipeline.repository.PipelineStepNodeRepository;
import com.custos.modules.pipeline.repository.StepExecutionLogRepository;
import com.custos.modules.vault.dto.CreateCredentialRequest;
import com.custos.modules.vault.dto.CredentialResponse;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.service.CredentialService;
import com.custos.shared.BadRequestException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
@ActiveProfiles("test")
public class ExternalNotifyTests {

    private static final String TOKEN = "test-token-" + UUID.randomUUID();
    private static final String TASK_ID = UUID.randomUUID().toString();

    @Autowired private ExternalNotifyService externalNotifyService;
    @Autowired private CredentialService credentialService;
    @Autowired private OutboundUrlValidator outboundUrlValidator;
    @Autowired private DagPipelineOrchestrator dagPipelineOrchestrator;
    @Autowired private PipelineDefinitionRepository pipelineDefinitionRepository;
    @Autowired private PipelineStepNodeRepository pipelineStepNodeRepository;
    @Autowired private StepExecutionLogRepository stepExecutionLogRepository;

    private HttpServer server;
    private int port;

    // พฤติกรรมของ mock server ปรับได้ต่อ test
    private volatile int sendStatus;
    private volatile String sendBody;
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastSendBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        sendStatus = 200;
        sendBody = "{\"success\":true,\"data\":{\"sentCount\":2,\"failedRecipients\":[]},\"message\":\"ส่งข้อความสำเร็จ\"}";

        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/external/notify/tasks", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            lastAuthHeader.set(auth);
            String path = exchange.getRequestURI().getPath();

            int status;
            String body;
            if (!("Bearer " + TOKEN).equals(auth)) {
                status = 401;
                body = "{\"success\":false,\"message\":\"unauthorized\"}";
            } else if (path.endsWith("/send")) {
                lastSendBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                status = sendStatus;
                body = sendBody;
            } else {
                status = 200;
                body = "{\"success\":true,\"data\":[{\"taskId\":\"" + TASK_ID + "\",\"title\":\"LINE Notify\","
                        + "\"chatbotId\":\"bot-1\",\"chatbotName\":\"VIP Concierge\",\"recipients\":[\"U1\",\"U2\"]}]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private UUID createCredential(String token) {
        CredentialResponse created = credentialService.createCredential(CreateCredentialRequest.builder()
                .name("ext-notify-" + UUID.randomUUID())
                .credentialType(CredentialType.EXTERNAL_NOTIFY)
                .host("http://127.0.0.1:" + port)
                .secretPassword(token)
                .build(), null, null);
        return created.getId();
    }

    @Test
    @DisplayName("Connect - lists external notify tasks using the bearer token from the vault")
    void testListTasks() {
        List<ExternalNotifyTaskDto> tasks = externalNotifyService.listTasks(createCredential(TOKEN));

        Assertions.assertEquals(1, tasks.size());
        Assertions.assertEquals(TASK_ID, tasks.get(0).getTaskId());
        Assertions.assertEquals("VIP Concierge", tasks.get(0).getChatbotName());
        Assertions.assertEquals(List.of("U1", "U2"), tasks.get(0).getRecipients());
        Assertions.assertEquals("Bearer " + TOKEN, lastAuthHeader.get());
    }

    @Test
    @DisplayName("Call - sends the message and reports sent count")
    void testSendSuccess() {
        ExternalNotifySendResult result = externalNotifyService.send(createCredential(TOKEN), TASK_ID, "สวัสดี");

        Assertions.assertEquals(2, result.getSentCount());
        Assertions.assertTrue(result.getFailedRecipients().isEmpty());
        Assertions.assertTrue(lastSendBody.get().contains("สวัสดี"));
    }

    @Test
    @DisplayName("Call - partial success exposes failed recipients")
    void testSendPartial() {
        sendBody = "{\"success\":true,\"data\":{\"sentCount\":1,\"failedRecipients\":[\"U2\"]},\"message\":\"ส่งข้อความสำเร็จบางส่วน\"}";

        ExternalNotifySendResult result = externalNotifyService.send(createCredential(TOKEN), TASK_ID, "hello");

        Assertions.assertEquals(1, result.getSentCount());
        Assertions.assertEquals(List.of("U2"), result.getFailedRecipients());
    }

    @Test
    @DisplayName("Upstream 401 is mapped to 400 and never leaks the token")
    void testUpstreamUnauthorizedMappedToBadRequest() {
        UUID credentialId = createCredential("wrong-token-value");

        BadRequestException ex = Assertions.assertThrows(BadRequestException.class,
                () -> externalNotifyService.listTasks(credentialId));

        Assertions.assertFalse(ex.getMessage().contains("wrong-token-value"));
    }

    @Test
    @DisplayName("Upstream 400 message is passed through")
    void testUpstreamBadRequest() {
        sendStatus = 400;
        sendBody = "{\"success\":false,\"message\":\"Task นี้ไม่ได้เปิดรับการแจ้งเตือนจากภายนอก\"}";
        UUID credentialId = createCredential(TOKEN);

        BadRequestException ex = Assertions.assertThrows(BadRequestException.class,
                () -> externalNotifyService.send(credentialId, TASK_ID, "hello"));

        Assertions.assertTrue(ex.getMessage().contains("ไม่ได้เปิดรับการแจ้งเตือน"));
    }

    @Test
    @DisplayName("Validation - blank message, bad task id and wrong credential type are rejected")
    void testInputValidation() {
        UUID credentialId = createCredential(TOKEN);
        Assertions.assertThrows(BadRequestException.class, () -> externalNotifyService.send(credentialId, TASK_ID, "  "));
        Assertions.assertThrows(BadRequestException.class, () -> externalNotifyService.send(credentialId, "not-a-uuid", "hello"));

        UUID genericId = credentialService.createCredential(CreateCredentialRequest.builder()
                .name("generic-" + UUID.randomUUID())
                .credentialType(CredentialType.GENERIC_SECRET)
                .secretPassword("secret")
                .build(), null, null).getId();
        Assertions.assertThrows(BadRequestException.class, () -> externalNotifyService.listTasks(genericId));
    }

    @Test
    @DisplayName("Vault - EXTERNAL_NOTIFY credential requires host and token")
    void testCredentialRequiresHostAndToken() {
        Assertions.assertThrows(BadRequestException.class, () -> credentialService.createCredential(
                CreateCredentialRequest.builder()
                        .name("no-host-" + UUID.randomUUID())
                        .credentialType(CredentialType.EXTERNAL_NOTIFY)
                        .secretPassword("t")
                        .build(), null, null));
        Assertions.assertThrows(BadRequestException.class, () -> credentialService.createCredential(
                CreateCredentialRequest.builder()
                        .name("no-token-" + UUID.randomUUID())
                        .credentialType(CredentialType.EXTERNAL_NOTIFY)
                        .host("example.com")
                        .build(), null, null));
    }

    @Test
    @DisplayName("OutboundUrlValidator - blocks cloud metadata, non-http schemes and user-info")
    void testOutboundUrlValidator() {
        Assertions.assertThrows(BadRequestException.class, () -> outboundUrlValidator.validate(URI.create("http://169.254.169.254/latest/meta-data")));
        Assertions.assertThrows(BadRequestException.class, () -> outboundUrlValidator.validate(URI.create("ftp://example.com/x")));
        Assertions.assertThrows(BadRequestException.class, () -> outboundUrlValidator.validate(URI.create("http://user:pw@127.0.0.1/x")));
        Assertions.assertDoesNotThrow(() -> outboundUrlValidator.validate(URI.create("http://127.0.0.1:" + port + "/api")));
    }

    @Test
    @DisplayName("Pipeline LINE_NOTIFY node - resolves placeholders and succeeds")
    void testPipelineLineNotifyNode() {
        UUID credentialId = createCredential(TOKEN);
        PipelineDefinition pipeline = pipelineDefinitionRepository.save(PipelineDefinition.builder()
                .name("LINE Notify Pipeline " + UUID.randomUUID())
                .isActive(true)
                .build());

        String config = String.format("{\"credentialId\":\"%s\",\"taskId\":\"%s\",\"message\":\"done: ${last_output_path}\"}",
                credentialId, TASK_ID);
        PipelineStepNode node = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("lineNotify")
                .nodeLabel("LINE Notify")
                .nodeType(TaskType.LINE_NOTIFY)
                .stepOrder(1)
                .configOverrideJson(config)
                .build();
        node.setId(UUID.randomUUID());
        pipelineStepNodeRepository.save(node);

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(pipeline.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        Assertions.assertTrue(lastSendBody.get().contains("done:"));
    }

    @Test
    @DisplayName("Pipeline LINE_NOTIFY node - upstream failure fails the pipeline")
    void testPipelineLineNotifyNodeFailure() {
        sendStatus = 400;
        sendBody = "{\"success\":false,\"message\":\"ส่งไม่สำเร็จกับทุกรายชื่อ\"}";
        UUID credentialId = createCredential(TOKEN);
        PipelineDefinition pipeline = pipelineDefinitionRepository.save(PipelineDefinition.builder()
                .name("LINE Notify Fail Pipeline " + UUID.randomUUID())
                .isActive(true)
                .build());

        PipelineStepNode node = PipelineStepNode.builder()
                .pipeline(pipeline)
                .nodeKey("lineNotify")
                .nodeLabel("LINE Notify")
                .nodeType(TaskType.LINE_NOTIFY)
                .stepOrder(1)
                .configOverrideJson(String.format("{\"credentialId\":\"%s\",\"taskId\":\"%s\",\"message\":\"x\"}", credentialId, TASK_ID))
                .build();
        node.setId(UUID.randomUUID());
        pipelineStepNodeRepository.save(node);

        PipelineExecution execution = dagPipelineOrchestrator.executePipeline(pipeline.getId(), "TEST_RUNNER", TriggerType.MANUAL);

        Assertions.assertEquals(ExecutionStatus.FAILED, execution.getStatus());
    }
}
