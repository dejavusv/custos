package com.custos.modules.vault.repository;

import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CredentialVaultRepository extends JpaRepository<CredentialVault, UUID> {

    Optional<CredentialVault> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    List<CredentialVault> findByCredentialType(CredentialType credentialType);

    Page<CredentialVault> findByCredentialType(CredentialType credentialType, Pageable pageable);

    Page<CredentialVault> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
