package com.custos.modules.vault.dto;

import com.custos.modules.vault.entity.CredentialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialResponse {

    private UUID id;
    private String name;
    private String description;
    private CredentialType credentialType;
    private String host;
    private Integer port;
    private String username;
    private String databaseName;
    private String secretMasked;
    private boolean hasSshKey;
    private String extraMetadata;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
}
