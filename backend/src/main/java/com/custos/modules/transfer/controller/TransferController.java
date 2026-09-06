package com.custos.modules.transfer.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.transfer.dto.*;
import com.custos.modules.transfer.engine.ChunkSplitterEngine;
import com.custos.modules.transfer.model.TransferManifest;
import com.custos.modules.transfer.model.TransferResult;
import com.custos.modules.transfer.service.ChecksumService;
import com.custos.modules.transfer.service.ResilientTransferService;
import com.custos.modules.transfer.service.StoragePrecheckService;
import com.custos.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class TransferController {

    private final StoragePrecheckService storagePrecheckService;
    private final ChunkSplitterEngine chunkSplitterEngine;
    private final ResilientTransferService resilientTransferService;
    private final ChecksumService checksumService;

    @PostMapping("/precheck")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<StoragePrecheckResult>> precheckStorage(
            @Valid @RequestBody StoragePrecheckRequest request
    ) {
        StoragePrecheckResult result = storagePrecheckService.checkStorage(request);
        return ResponseEntity.ok(ApiResponse.ok(result, result.getMessage()));
    }

    @PostMapping("/split")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<TransferManifest>> splitFile(
            @Valid @RequestBody SplitFileRequest request
    ) {
        File source = new File(request.getSourceFilePath());
        File outDir = request.getOutputDirectory() != null ? new File(request.getOutputDirectory()) : source.getParentFile();
        TransferManifest manifest = chunkSplitterEngine.splitFile(source, outDir, request.getChunkSizeBytes());
        return ResponseEntity.ok(ApiResponse.ok(manifest, "File successfully split into " + manifest.getTotalChunks() + " chunks"));
    }

    @PostMapping("/merge")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> mergeChunks(
            @Valid @RequestBody MergeFileRequest request
    ) {
        List<File> chunkFiles = request.getChunkFilePaths().stream().map(File::new).collect(Collectors.toList());
        File dest = new File(request.getDestinationFilePath());
        File merged = chunkSplitterEngine.mergeChunks(chunkFiles, dest);
        String sha256 = checksumService.calculateSha256(merged);

        Map<String, Object> data = new HashMap<>();
        data.put("mergedFilePath", merged.getAbsolutePath());
        data.put("mergedSizeBytes", merged.length());
        data.put("checksumSha256", sha256);

        return ResponseEntity.ok(ApiResponse.ok(data, "Chunks merged successfully"));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<TransferResult>> uploadFile(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        TransferResult result = resilientTransferService.transferFile(request, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result, "Resilient transfer completed successfully"));
    }
}
