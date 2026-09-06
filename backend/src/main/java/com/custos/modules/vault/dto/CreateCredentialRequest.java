package com.custos.modules.vault.dto;

import com.custos.modules.vault.entity.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCredentialRequest {

    @NotBlank(message = "Credential profile name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @NotNull(message = "Credential type is required")
    private CredentialType credentialType;

    private String host;

    private Integer port;

    private String username;

    private String databaseName;

    // Sensitive payload fields - will be encrypted using AES-256-GCM
    private String secretPassword;

    private String sshPrivateKey;

    private String sshPassphrase;

    private String extraMetadata;
}
