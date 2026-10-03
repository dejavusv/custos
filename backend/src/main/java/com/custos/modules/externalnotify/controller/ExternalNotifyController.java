package com.custos.modules.externalnotify.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.externalnotify.dto.ExternalNotifySendRequest;
import com.custos.modules.externalnotify.dto.ExternalNotifySendResult;
import com.custos.modules.externalnotify.dto.ExternalNotifyTaskDto;
import com.custos.modules.externalnotify.service.ExternalNotifyService;
import com.custos.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/external-notify")
@RequiredArgsConstructor
public class ExternalNotifyController {

    private static final int AUDIT_MESSAGE_PREVIEW_CHARS = 100;

    private final ExternalNotifyService externalNotifyService;
    private final AuditLogService auditLogService;

    @GetMapping("/tasks")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<List<ExternalNotifyTaskDto>>> listTasks(@RequestParam UUID credentialId) {
        return ResponseEntity.ok(ApiResponse.ok(externalNotifyService.listTasks(credentialId)));
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<ExternalNotifySendResult>> send(
            @Valid @RequestBody ExternalNotifySendRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        ExternalNotifySendResult result = externalNotifyService.send(
                request.getCredentialId(), request.getTaskId(), request.getMessage());

        if (currentUser != null) {
            String preview = request.getMessage().length() > AUDIT_MESSAGE_PREVIEW_CHARS
                    ? request.getMessage().substring(0, AUDIT_MESSAGE_PREVIEW_CHARS) + "..."
                    : request.getMessage();
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "EXTERNAL_NOTIFY_SEND",
                    "EXTERNAL_NOTIFY:" + request.getTaskId(),
                    "sent=" + result.getSentCount() + ", failed=" + result.getFailedRecipients().size() + ", message=" + preview,
                    servletRequest
            );
        }
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }
}
