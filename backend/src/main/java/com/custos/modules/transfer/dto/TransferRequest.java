package com.custos.modules.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Source file path is required")
    private String sourceFilePath;

    // Vault profile reference
    private UUID credentialId;

    // Optional execution context reference for live WebSocket broadcasting
    private UUID executionId;

    // Direct parameters fallback
    private String protocol; // "SFTP" or "FTP"
    private String host;
    private int port;
    private String username;
    private String password;
    private String sshPrivateKey;
    private String sshPassphrase;

    @Builder.Default
    private String remoteDirectory = "/upload";

    @Builder.Default
    private long chunkSizeBytes = 50 * 1024 * 1024L; // 50MB

    @Builder.Default
    private int maxRetriesPerChunk = 3;

    @Builder.Default
    private boolean uploadManifest = true;
}
