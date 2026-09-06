package com.custos.modules.vault.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCredentialRequest {

    @NotBlank(message = "Credential profile name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    private String host;

    private Integer port;

    private String username;

    private String databaseName;

    // Optional: If blank/null, previous encrypted secret remains intact
    private String secretPassword;

    private String sshPrivateKey;

    private String sshPassphrase;

    private String extraMetadata;
}
