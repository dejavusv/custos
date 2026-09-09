package com.custos.modules.transfer.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.transfer.client.FtpTransferClient;
import com.custos.modules.transfer.client.RemoteTransferClient;
import com.custos.modules.transfer.client.SftpTransferClient;
import com.custos.modules.transfer.dto.TransferRequest;
import com.custos.modules.transfer.engine.ChunkSplitterEngine;
import com.custos.modules.transfer.model.ChunkMetadata;
import com.custos.modules.transfer.model.TransferManifest;
import com.custos.modules.transfer.model.TransferResult;
import com.custos.modules.vault.dto.DecryptedSecretPayload;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import com.custos.modules.vault.repository.CredentialVaultRepository;
import com.custos.modules.vault.service.CredentialService;
import com.custos.shared.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResilientTransferService {

    private final ChunkSplitterEngine chunkSplitterEngine;
    private final CredentialVaultRepository credentialVaultRepository;
    private final CredentialService credentialService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final com.custos.modules.console.ExecutionProgressBroadcaster progressBroadcaster;

    /**
     * Executes resilient sequential transfer with per-chunk auto-retry.
     */
    public TransferResult transferFile(
            TransferRequest request,
            UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        if (request == null || request.getSourceFilePath() == null || request.getSourceFilePath().trim().isEmpty()) {
            throw new IllegalArgumentException("Source file path is required");
        }

        File sourceFile = new File(request.getSourceFilePath().trim());
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IllegalArgumentException("Source file does not exist: " + sourceFile);
        }

        Instant startTime = Instant.now();
        long chunkSize = request.getChunkSizeBytes() > 0 ? request.getChunkSizeBytes() : ChunkSplitterEngine.DEFAULT_CHUNK_SIZE_BYTES;

        File tempSplitDir = null;
        TransferManifest manifest;

        try {
            // Always use an isolated temp directory for chunks so cleanup in finally is guaranteed
            tempSplitDir = Files.createTempDirectory("custos-split-").toFile();
            manifest = chunkSplitterEngine.splitFile(sourceFile, tempSplitDir, chunkSize);

            // Resolve target client
            RemoteTransferClient client = resolveTransferClient(request);
            String remoteDir = request.getRemoteDirectory() != null ? request.getRemoteDirectory().trim() : "/upload";
            int maxRetries = request.getMaxRetriesPerChunk() > 0 ? request.getMaxRetriesPerChunk() : 3;

            int successfulChunks = 0;
            int totalRetries = 0;
            long totalBytesTransferred = 0;
            String targetType = "REMOTE";

            try (client) {
                client.connect();

                for (ChunkMetadata chunk : manifest.getChunks()) {
                    File chunkFile = new File(chunk.getFilePath());
                    boolean uploaded = false;
                    int chunkAttempt = 0;

                    log.info("Starting transfer of chunk {}/{} ({})",
                            chunk.getPartNumber(), manifest.getTotalChunks(), chunk.getFileName());

                    if (request.getExecutionId() != null && progressBroadcaster != null) {
                        progressBroadcaster.broadcastLog(request.getExecutionId(), "Transfer", "INFO",
                                "Starting upload: chunk " + chunk.getPartNumber() + "/" + manifest.getTotalChunks() + " (" + chunk.getFileName() + ")");
                    }

                    while (!uploaded && chunkAttempt <= maxRetries) {
                        try {
                            if (chunkAttempt > 0) {
                                totalRetries++;
                                chunk.setRetries(chunkAttempt);
                                long backoff = (long) Math.pow(2, chunkAttempt - 1) * 1000L;
                                log.warn("Retrying chunk {} (attempt {}/{}) after {}ms backoff...",
                                        chunk.getPartNumber(), chunkAttempt, maxRetries, backoff);
                                if (request.getExecutionId() != null && progressBroadcaster != null) {
                                    progressBroadcaster.broadcastLog(request.getExecutionId(), "Transfer", "WARN",
                                            "Network retry for chunk " + chunk.getPartNumber() + " (attempt " + chunkAttempt + "/" + maxRetries + ")");
                                }
                                Thread.sleep(backoff);
                            }

                            client.uploadFile(chunkFile, remoteDir, chunk.getFileName());

                            // Verify remote size
                            long remoteSize = client.getRemoteFileSize(remoteDir, chunk.getFileName());
                            if (remoteSize == chunk.getSizeBytes()) {
                                uploaded = true;
                                chunk.setStatus("UPLOADED");
                                successfulChunks++;
                                totalBytesTransferred += chunk.getSizeBytes();
                                log.info("Successfully uploaded and verified chunk {}/{} ({} bytes)",
                                        chunk.getPartNumber(), manifest.getTotalChunks(), chunk.getSizeBytes());

                                if (request.getExecutionId() != null && progressBroadcaster != null) {
                                    int pct = manifest.getTotalChunks() > 0 ? (successfulChunks * 100) / manifest.getTotalChunks() : 100;
                                    progressBroadcaster.broadcastProgress(com.custos.modules.console.ProgressUpdateDto.builder()
                                            .executionId(request.getExecutionId())
                                            .stepName("Transfer")
                                            .percentage(pct)
                                            .currentChunk(chunk.getPartNumber())
                                            .totalChunks(manifest.getTotalChunks())
                                            .bytesTransferred(totalBytesTransferred)
                                            .totalBytes(manifest.getOriginalSizeBytes())
                                            .status("TRANSFERRING")
                                            .build());
                                    progressBroadcaster.broadcastLog(request.getExecutionId(), "Transfer", "SUCCESS",
                                            "Verified chunk " + chunk.getPartNumber() + "/" + manifest.getTotalChunks() + " on remote destination");
                                }
                            } else {
                                throw new RuntimeException("Remote size mismatch for chunk " + chunk.getFileName() +
                                        ": expected " + chunk.getSizeBytes() + ", got " + remoteSize);
                            }

                        } catch (Exception e) {
                            chunkAttempt++;
                            if (chunkAttempt > maxRetries) {
                                chunk.setStatus("FAILED");
                                String error = String.format("Failed to upload chunk %s after %d retries: %s",
                                        chunk.getFileName(), maxRetries, e.getMessage());
                                log.error(error);
                                throw new RuntimeException(error, e);
                            }
                        }
                    }
                }

                // Upload manifest.json if requested
                if (request.isUploadManifest()) {
                    File manifestFile = File.createTempFile("manifest-", ".json");
                    objectMapper.writeValue(manifestFile, manifest);
                    client.uploadFile(manifestFile, remoteDir, sourceFile.getName() + ".manifest.json");
                    manifestFile.delete();
                    log.info("Uploaded transfer manifest to remote destination");
                }
            }

            long durationMs = Duration.between(startTime, Instant.now()).toMillis();

            if (currentUser != null) {
                auditLogService.logFromRequest(
                        currentUser.getId(),
                        currentUser.getUsername(),
                        "SPLIT_TRANSFER",
                        "TRANSFER:" + sourceFile.getName(),
                        String.format("Transferred %d chunks (%d bytes) to %s in %dms (retries: %d)",
                                successfulChunks, totalBytesTransferred, remoteDir, durationMs, totalRetries),
                        servletRequest
                );
            }

            return TransferResult.builder()
                    .success(true)
                    .targetType(targetType)
                    .remoteDestination(remoteDir)
                    .totalChunks(manifest.getTotalChunks())
                    .successfulChunks(successfulChunks)
                    .failedChunks(0)
                    .totalBytesTransferred(totalBytesTransferred)
                    .totalRetries(totalRetries)
                    .durationMs(durationMs)
                    .manifest(manifest)
                    .executedAt(Instant.now())
                    .build();

        } catch (Exception e) {
            log.error("Transfer failed: {}", e.getMessage(), e);
            throw new RuntimeException("Transfer process aborted: " + e.getMessage(), e);
        } finally {
            // Clean up temporary split directory if one was created
            if (tempSplitDir != null && tempSplitDir.exists()) {
                File[] files = tempSplitDir.listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                tempSplitDir.delete();
            }
        }
    }

    private RemoteTransferClient resolveTransferClient(TransferRequest request) {
        if (request.getCredentialId() != null) {
            CredentialVault vault = credentialVaultRepository.findById(request.getCredentialId())
                    .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found: " + request.getCredentialId()));

            if (vault.getHost() == null || vault.getHost().trim().isEmpty()) {
                throw new com.custos.shared.BadRequestException("Credential Profile '" + vault.getName() + "' ไม่มีข้อมูล Host / IP Address");
            }

            DecryptedSecretPayload secret = credentialService.getDecryptedSecret(vault.getId());
            String password = secret != null ? secret.getPassword() : null;
            String privateKey = secret != null ? secret.getSshPrivateKey() : null;
            String passphrase = secret != null ? secret.getSshPassphrase() : null;

            if (vault.getCredentialType() == CredentialType.FTP || vault.getCredentialType() == CredentialType.FTPS) {
                boolean isFtps = vault.getCredentialType() == CredentialType.FTPS;
                boolean trustSelfSigned = true;

                if (vault.getExtraMetadata() != null && !vault.getExtraMetadata().isBlank()) {
                    try {
                        var node = objectMapper.readTree(vault.getExtraMetadata());
                        if (node.has("ftpEncryption")) {
                            String enc = node.get("ftpEncryption").asText();
                            isFtps = "EXPLICIT_TLS".equalsIgnoreCase(enc) || "FTPS".equalsIgnoreCase(enc);
                        } else if (node.has("isFtps")) {
                            isFtps = node.get("isFtps").asBoolean();
                        }
                        if (node.has("trustSelfSigned")) {
                            trustSelfSigned = node.get("trustSelfSigned").asBoolean();
                        }
                    } catch (Exception e) {
                        log.warn("Could not parse extraMetadata for FTP credential {}: {}", vault.getName(), e.getMessage());
                    }
                }

                return new FtpTransferClient(vault.getHost(), vault.getPort(), vault.getUsername(), password, isFtps, trustSelfSigned);
            } else {
                // SFTP
                return new SftpTransferClient(vault.getHost(), vault.getPort(), vault.getUsername(), password, privateKey, passphrase);
            }
        }

        // Direct parameters fallback: host is strictly required
        if (request.getHost() == null || request.getHost().trim().isEmpty()) {
            throw new com.custos.shared.BadRequestException("กรุณาระบุ Credential Profile จาก Vault หรือระบุ Host/IP ปลายทางสำหรับการโอนถ่ายไฟล์");
        }

        if ("FTP".equalsIgnoreCase(request.getProtocol()) || "FTPS".equalsIgnoreCase(request.getProtocol())) {
            boolean isFtps = "FTPS".equalsIgnoreCase(request.getProtocol())
                    || Boolean.TRUE.equals(request.getIsFtps())
                    || "EXPLICIT_TLS".equalsIgnoreCase(request.getFtpEncryption());
            return new FtpTransferClient(request.getHost(), request.getPort(), request.getUsername(), request.getPassword(), isFtps, true);
        } else {
            return new SftpTransferClient(request.getHost(), request.getPort(), request.getUsername(), request.getPassword(), request.getSshPrivateKey(), request.getSshPassphrase());
        }
    }
}
