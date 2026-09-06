package com.custos.modules.vault.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecryptedSecretPayload {
    private String password;
    private String sshPrivateKey;
    private String sshPassphrase;
    private String apiKey;
    private String customToken;
}
