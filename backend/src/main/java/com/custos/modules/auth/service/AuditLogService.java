package com.custos.modules.auth.service;

import com.custos.modules.auth.entity.AuditLog;
import com.custos.modules.auth.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void logAction(UUID userId, String username, String action, String targetResource, String ipAddress, String details) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .targetResource(targetResource)
                    .ipAddress(ipAddress)
                    .details(details)
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to write audit log: {}", e.getMessage(), e);
        }
    }

    public void logFromRequest(UUID userId, String username, String action, String targetResource, String details, HttpServletRequest request) {
        String ipAddress = extractClientIp(request);
        logAction(userId, username, action, targetResource, ipAddress, details);
    }

    public Page<AuditLog> getLogs(String username, String action, Pageable pageable) {
        boolean hasUsername = username != null && !username.trim().isEmpty();
        boolean hasAction = action != null && !action.trim().isEmpty();

        if (hasUsername && hasAction) {
            return auditLogRepository.findByUsernameContainingIgnoreCaseAndActionContainingIgnoreCase(username.trim(), action.trim(), pageable);
        } else if (hasUsername) {
            return auditLogRepository.findByUsernameContainingIgnoreCase(username.trim(), pageable);
        } else if (hasAction) {
            return auditLogRepository.findByActionContainingIgnoreCase(action.trim(), pageable);
        } else {
            return auditLogRepository.findAll(pageable);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "UNKNOWN";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
