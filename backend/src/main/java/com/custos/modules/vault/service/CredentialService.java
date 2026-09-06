package com.custos.modules.vault.service;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuditLogService;
import com.custos.modules.vault.dto.*;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import com.custos.modules.vault.repository.CredentialVaultRepository;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialVaultRepository credentialVaultRepository;
    private final VaultService vaultService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<CredentialResponse> getCredentials(CredentialType type, String search, Pageable pageable) {
        Page<CredentialVault> page;
        if (type != null) {
            page = credentialVaultRepository.findByCredentialType(type, pageable);
        } else if (search != null && !search.trim().isEmpty()) {
            page = credentialVaultRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
        } else {
            page = credentialVaultRepository.findAll(pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CredentialResponse> getAllCredentials() {
        return credentialVaultRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CredentialResponse getCredentialById(UUID id) {
        CredentialVault vault = credentialVaultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found with ID: " + id));
        return mapToResponse(vault);
    }

    /**
     * Internal method for Task and Pipeline execution engines to retrieve decrypted secrets.
     */
    @Transactional(readOnly = true)
    public DecryptedSecretPayload getDecryptedSecret(UUID id) {
        CredentialVault vault = credentialVaultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found with ID: " + id));
        return vaultService.decryptJson(vault.getEncryptedData(), DecryptedSecretPayload.class);
    }

    @Transactional
    public CredentialResponse createCredential(CreateCredentialRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        if (credentialVaultRepository.existsByName(request.getName())) {
            throw new BadRequestException("Credential profile name '" + request.getName() + "' already exists");
        }

        // Build decrypted payload structure
        DecryptedSecretPayload payload = DecryptedSecretPayload.builder()
                .password(request.getSecretPassword())
                .sshPrivateKey(request.getSshPrivateKey())
                .sshPassphrase(request.getSshPassphrase())
                .build();

        // Encrypt using AES-256-GCM
        String encryptedData = vaultService.encryptJson(payload);

        CredentialVault credentialVault = CredentialVault.builder()
                .name(request.getName())
                .description(request.getDescription())
                .credentialType(request.getCredentialType())
                .encryptedData(encryptedData)
                .host(request.getHost())
                .port(request.getPort())
                .username(request.getUsername())
                .databaseName(request.getDatabaseName())
                .extraMetadata(request.getExtraMetadata())
                .build();

        CredentialVault saved = credentialVaultRepository.save(credentialVault);

        // Audit Log
        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "CREATE_CREDENTIAL",
                    "CREDENTIAL_VAULT:" + saved.getName(),
                    "Created credential profile type: " + saved.getCredentialType(),
                    servletRequest
            );
        }

        log.info("Created credential profile '{}' of type '{}'", saved.getName(), saved.getCredentialType());
        return mapToResponse(saved);
    }

    @Transactional
    public CredentialResponse updateCredential(UUID id, UpdateCredentialRequest request, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        CredentialVault vault = credentialVaultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found with ID: " + id));

        if (credentialVaultRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BadRequestException("Credential profile name '" + request.getName() + "' is already in use");
        }

        vault.setName(request.getName());
        vault.setDescription(request.getDescription());
        vault.setHost(request.getHost());
        vault.setPort(request.getPort());
        vault.setUsername(request.getUsername());
        vault.setDatabaseName(request.getDatabaseName());
        vault.setExtraMetadata(request.getExtraMetadata());

        // Check if secrets need updating
        boolean updatePassword = request.getSecretPassword() != null && !request.getSecretPassword().trim().isEmpty() && !request.getSecretPassword().equals("********");
        boolean updateSshKey = request.getSshPrivateKey() != null && !request.getSshPrivateKey().trim().isEmpty();
        boolean updatePassphrase = request.getSshPassphrase() != null;

        if (updatePassword || updateSshKey || updatePassphrase) {
            DecryptedSecretPayload existingPayload;
            try {
                existingPayload = vaultService.decryptJson(vault.getEncryptedData(), DecryptedSecretPayload.class);
            } catch (Exception e) {
                existingPayload = new DecryptedSecretPayload();
            }

            if (updatePassword) {
                existingPayload.setPassword(request.getSecretPassword());
            }
            if (updateSshKey) {
                existingPayload.setSshPrivateKey(request.getSshPrivateKey());
            }
            if (updatePassphrase) {
                existingPayload.setSshPassphrase(request.getSshPassphrase());
            }

            vault.setEncryptedData(vaultService.encryptJson(existingPayload));
        }

        CredentialVault updated = credentialVaultRepository.save(vault);

        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "UPDATE_CREDENTIAL",
                    "CREDENTIAL_VAULT:" + updated.getName(),
                    "Updated credential profile",
                    servletRequest
            );
        }

        log.info("Updated credential profile '{}' (ID: {})", updated.getName(), updated.getId());
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCredential(UUID id, UserPrincipal currentUser, HttpServletRequest servletRequest) {
        CredentialVault vault = credentialVaultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Credential profile not found with ID: " + id));

        credentialVaultRepository.delete(vault);

        if (currentUser != null) {
            auditLogService.logFromRequest(
                    currentUser.getId(),
                    currentUser.getUsername(),
                    "DELETE_CREDENTIAL",
                    "CREDENTIAL_VAULT:" + vault.getName(),
                    "Deleted credential profile",
                    servletRequest
            );
        }

        log.info("Deleted credential profile '{}' (ID: {})", vault.getName(), id);
    }

    private CredentialResponse mapToResponse(CredentialVault entity) {
        boolean hasSshKey = false;
        try {
            DecryptedSecretPayload payload = vaultService.decryptJson(entity.getEncryptedData(), DecryptedSecretPayload.class);
            hasSshKey = payload != null && payload.getSshPrivateKey() != null && !payload.getSshPrivateKey().trim().isEmpty();
        } catch (Exception ignored) {
        }

        return CredentialResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .credentialType(entity.getCredentialType())
                .host(entity.getHost())
                .port(entity.getPort())
                .username(entity.getUsername())
                .databaseName(entity.getDatabaseName())
                .secretMasked("********")
                .hasSshKey(hasSshKey)
                .extraMetadata(entity.getExtraMetadata())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .createdBy(entity.getCreatedBy())
                .build();
    }
}
