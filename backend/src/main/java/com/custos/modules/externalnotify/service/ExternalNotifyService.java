package com.custos.modules.externalnotify.service;

import com.custos.modules.externalnotify.client.ExternalNotifyClient;
import com.custos.modules.externalnotify.dto.ExternalNotifySendResult;
import com.custos.modules.externalnotify.dto.ExternalNotifyTaskDto;
import com.custos.modules.vault.dto.DecryptedSecretPayload;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import com.custos.modules.vault.repository.CredentialVaultRepository;
import com.custos.modules.vault.service.CredentialService;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExternalNotifyService {

    static final int MAX_MESSAGE_CHARS = 5000;

    private final CredentialVaultRepository credentialVaultRepository;
    private final CredentialService credentialService;
    private final ExternalNotifyClient client;
    private final ObjectMapper objectMapper;

    /** Connect: ดึงรายการ Task ที่เปิดรับการแจ้งเตือนจากภายนอก */
    public List<ExternalNotifyTaskDto> listTasks(UUID credentialId) {
        Target target = resolveTarget(credentialId);
        JsonNode root = client.call("GET", URI.create(target.baseUrl() + "/external/notify/tasks"), target.token(), null);

        List<ExternalNotifyTaskDto> tasks = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            tasks.add(ExternalNotifyTaskDto.builder()
                    .taskId(item.path("taskId").asText(""))
                    .title(item.path("title").asText(""))
                    .chatbotId(item.path("chatbotId").asText(""))
                    .chatbotName(item.hasNonNull("chatbotName") ? item.get("chatbotName").asText() : null)
                    .recipients(textList(item.path("recipients")))
                    .build());
        }
        return tasks;
    }

    /** Call: ส่งข้อความไปยังลูกค้าทุกรายที่ผูกกับ Task */
    public ExternalNotifySendResult send(UUID credentialId, String taskId, String message) {
        if (message == null || message.isBlank()) {
            throw new BadRequestException("กรุณาระบุข้อความที่จะแจ้งเตือน");
        }
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new BadRequestException("ข้อความต้องไม่เกิน " + MAX_MESSAGE_CHARS + " ตัวอักษร");
        }
        UUID taskUuid = parseTaskId(taskId);

        Target target = resolveTarget(credentialId);
        ObjectNode body = objectMapper.createObjectNode().put("message", message);
        JsonNode root = client.call("POST",
                URI.create(target.baseUrl() + "/external/notify/tasks/" + taskUuid + "/send"),
                target.token(), body.toString());

        JsonNode data = root.path("data");
        return ExternalNotifySendResult.builder()
                .sentCount(data.path("sentCount").asInt(0))
                .failedRecipients(textList(data.path("failedRecipients")))
                .message(root.hasNonNull("message") ? root.get("message").asText() : null)
                .build();
    }

    private UUID parseTaskId(String taskId) {
        try {
            return UUID.fromString(taskId == null ? "" : taskId.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Task ID ไม่ถูกต้อง");
        }
    }

    private List<String> textList(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.asText());
        }
        return values;
    }

    private Target resolveTarget(UUID credentialId) {
        if (credentialId == null) {
            throw new BadRequestException("กรุณาเลือก Credential สำหรับ External Notify");
        }
        CredentialVault vault = credentialVaultRepository.findById(credentialId)
                .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found with ID: " + credentialId));
        if (vault.getCredentialType() != CredentialType.EXTERNAL_NOTIFY) {
            throw new BadRequestException("Credential นี้ไม่ใช่ชนิด EXTERNAL_NOTIFY");
        }

        DecryptedSecretPayload secret = credentialService.getDecryptedSecret(credentialId);
        if (secret.getPassword() == null || secret.getPassword().isBlank()) {
            throw new BadRequestException("Credential นี้ยังไม่ได้ตั้งค่า Auth Token");
        }
        return new Target(buildBaseUrl(vault.getHost(), vault.getPort()), secret.getPassword());
    }

    /** host อาจเป็น domain เฉยๆ (ใช้ https) หรือ URL เต็ม เช่น http://localhost:8080; ต่อท้าย /api ให้ถ้ายังไม่มี */
    static String buildBaseUrl(String host, Integer port) {
        if (host == null || host.isBlank()) {
            throw new BadRequestException("Credential นี้ยังไม่ได้ตั้งค่า Domain");
        }
        String raw = host.trim();
        if (!raw.contains("://")) {
            raw = "https://" + raw;
        }
        try {
            URI parsed = new URI(raw);
            if (parsed.getHost() == null) {
                throw new BadRequestException("รูปแบบ Domain ไม่ถูกต้อง");
            }
            String path = parsed.getPath() == null ? "" : parsed.getPath().replaceAll("/+$", "");
            if (!path.endsWith("/api")) {
                path = path + "/api";
            }
            int effectivePort = parsed.getPort() != -1 ? parsed.getPort() : (port != null ? port : -1);
            return new URI(parsed.getScheme(), parsed.getUserInfo(), parsed.getHost(), effectivePort, path, null, null).toString();
        } catch (URISyntaxException e) {
            throw new BadRequestException("รูปแบบ Domain ไม่ถูกต้อง");
        }
    }

    private record Target(String baseUrl, String token) {}
}
