package com.custos.modules.drive.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.drive.dto.DriveUploadResponse;
import com.custos.modules.drive.model.FileUploadAuditRecord;
import com.custos.modules.drive.service.DriveUploadFacadeService;
import com.custos.modules.drive.service.FirestoreAuditService;
import com.custos.shared.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/drive")
@RequiredArgsConstructor
public class GoogleDriveController {

    private final DriveUploadFacadeService driveUploadFacadeService;
    private final FirestoreAuditService firestoreAuditService;

    /**
     * Uploads a file to Google Drive and logs metadata to Firebase Cloud Firestore.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<DriveUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("systemSource") String systemSource,
            @RequestParam(value = "folderId", required = false) String folderId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) throws IOException {
        DriveUploadResponse response = driveUploadFacadeService.uploadAndRecordAudit(
                file,
                systemSource,
                folderId,
                currentUser
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "File uploaded to Google Drive and logged to Firestore successfully"));
    }

    /**
     * Retrieves upload audit logs from Firebase Cloud Firestore.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<ApiResponse<List<FileUploadAuditRecord>>> getUploadHistory(
            @RequestParam(value = "systemSource", required = false) String systemSource,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit
    ) {
        List<FileUploadAuditRecord> logs = firestoreAuditService.queryAuditLogs(systemSource, status, limit);
        return ResponseEntity.ok(ApiResponse.ok(logs, "File upload audit logs retrieved successfully"));
    }

    /**
     * Diagnostic endpoint to check Google Drive & Firestore readiness.
     */
    @GetMapping("/health")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_VIEWER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("service", "google-drive-firebase-integration");
        statusMap.put("status", "UP");
        return ResponseEntity.ok(ApiResponse.ok(statusMap));
    }
}
