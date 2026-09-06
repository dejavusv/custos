package com.custos.modules.vault.entity;

import com.custos.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "credentials_vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CredentialVault extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false, length = 50)
    private CredentialType credentialType;

    @Column(name = "encrypted_data", nullable = false, columnDefinition = "TEXT")
    private String encryptedData;

    @Column(name = "host", length = 255)
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "database_name", length = 100)
    private String databaseName;

    @Column(name = "extra_metadata", columnDefinition = "TEXT")
    private String extraMetadata;
}
