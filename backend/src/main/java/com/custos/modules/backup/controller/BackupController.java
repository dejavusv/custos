package com.custos.modules.backup.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.backup.dto.*;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.service.BackupService;
import com.custos.modules.backup.service.RetentionCleanupService;
import com.custos.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;
    private final RetentionCleanupService retentionCleanupService;

    @PostMapping("/database")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<BackupResult>> triggerDatabaseBackup(
            @Valid @RequestBody DatabaseBackupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        BackupResult result = backupService.backupDatabase(request, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result, "Database backup completed successfully"));
    }

    @PostMapping("/filesystem")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<BackupResult>> triggerFileSystemBackup(
            @Valid @RequestBody FileBackupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        BackupResult result = backupService.backupFileSystem(request, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result, "File system backup completed successfully"));
    }

    @PostMapping("/retention-cleanup")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<RetentionCleanupResult>> triggerRetentionCleanup(
            @Valid @RequestBody RetentionCleanupRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        RetentionCleanupResult result = retentionCleanupService.executeRetentionCleanup(request, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    @GetMapping("/storage/browse")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<StorageBrowseResponse>> browseStorage(
            @RequestParam(value = "path", required = false) String path
    ) {
        StorageBrowseResponse response = backupService.browseStorage(path);
        return ResponseEntity.ok(ApiResponse.ok(response, "Storage directory listed successfully"));
    }

    @PostMapping("/storage/mkdir")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<StorageItemDto>> createDirectory(
            @Valid @RequestBody CreateDirectoryRequest request
    ) {
        StorageItemDto created = backupService.createDirectory(request.getParentPath(), request.getFolderName());
        return ResponseEntity.ok(ApiResponse.ok(created, "Directory created successfully"));
    }
}
