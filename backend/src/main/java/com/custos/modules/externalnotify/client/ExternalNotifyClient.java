package com.custos.modules.externalnotify.client;

import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client สำหรับเรียก External Notify API ของ CCenterV2
 * ข้อความ error และ log ต้องไม่มี token หรือ URL เต็ม
 */
@Slf4j
@Component
public class ExternalNotifyClient {

    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final int MAX_UPSTREAM_MESSAGE_CHARS = 200;

    private final ObjectMapper objectMapper;
    private final OutboundUrlValidator urlValidator;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ExternalNotifyClient(ObjectMapper objectMapper, OutboundUrlValidator urlValidator) {
        this.objectMapper = objectMapper;
        this.urlValidator = urlValidator;
    }

    /**
     * ส่ง request แล้วคืน envelope ({success,data,message}) เมื่อได้ 2xx และ success=true
     * ส่วน upstream 401/403 จะถูกแปลงเป็น 400 เสมอ เพื่อไม่ให้ axios interceptor ฝั่งหน้าบ้านทำ refresh token วนลูป
     */
    public JsonNode call(String method, URI uri, String token, String jsonBody) {
        urlValidator.validate(uri);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (jsonBody != null) {
            builder.header("Content-Type", "application/json; charset=utf-8")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        int status;
        String body;
        try {
            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            try (InputStream in = response.body()) {
                body = new String(in.readNBytes(MAX_BODY_BYTES), StandardCharsets.UTF_8);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("การเชื่อมต่อ External Notify ถูกยกเลิก");
        } catch (IOException e) {
            log.warn("External Notify connection failed: {}", e.getClass().getSimpleName());
            throw new BadRequestException("ไม่สามารถเชื่อมต่อ External Notify ได้ กรุณาตรวจสอบ Domain และการเชื่อมต่อเครือข่าย");
        }

        JsonNode root = parse(body);
        String upstreamMessage = upstreamMessage(root);

        if (status == 401 || status == 403) {
            throw new BadRequestException("External Notify Token ไม่ถูกต้องหรือไม่มีสิทธิ์");
        }
        if (status == 404) {
            throw new ResourceNotFoundException(upstreamMessage != null ? "External Notify: " + upstreamMessage : "ไม่พบข้อมูลบน External Notify");
        }
        if (status >= 400 && status < 500) {
            throw new BadRequestException("External Notify: " + (upstreamMessage != null ? upstreamMessage : "คำขอไม่ถูกต้อง (HTTP " + status + ")"));
        }
        if (status < 200 || status >= 300) {
            throw new BadRequestException("External Notify ตอบกลับผิดปกติ (HTTP " + status + ")");
        }
        if (root == null || !root.path("success").asBoolean(false)) {
            throw new BadRequestException("External Notify: " + (upstreamMessage != null ? upstreamMessage : "ตอบกลับไม่สำเร็จ"));
        }
        return root;
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return null;
        }
    }

    private String upstreamMessage(JsonNode root) {
        if (root == null || !root.hasNonNull("message")) {
            return null;
        }
        String message = root.get("message").asText();
        if (message.length() > MAX_UPSTREAM_MESSAGE_CHARS) {
            message = message.substring(0, MAX_UPSTREAM_MESSAGE_CHARS);
        }
        return message.isBlank() ? null : message;
    }
}
