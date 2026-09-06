package com.custos.modules.vault.controller;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.vault.dto.CreateCredentialRequest;
import com.custos.modules.vault.dto.CredentialResponse;
import com.custos.modules.vault.dto.UpdateCredentialRequest;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.service.CredentialService;
import com.custos.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vault/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<CredentialResponse>>> getCredentials(
            @RequestParam(required = false) CredentialType type,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CredentialResponse> result = credentialService.getCredentials(type, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result, "Credentials retrieved successfully"));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR')")
    public ResponseEntity<ApiResponse<List<CredentialResponse>>> getAllCredentials() {
        List<CredentialResponse> result = credentialService.getAllCredentials();
        return ResponseEntity.ok(ApiResponse.ok(result, "All credentials retrieved"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CredentialResponse>> getCredentialById(@PathVariable UUID id) {
        CredentialResponse result = credentialService.getCredentialById(id);
        return ResponseEntity.ok(ApiResponse.ok(result, "Credential retrieved"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CredentialResponse>> createCredential(
            @Valid @RequestBody CreateCredentialRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        CredentialResponse result = credentialService.createCredential(request, currentUser, servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Credential profile created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<CredentialResponse>> updateCredential(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCredentialRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        CredentialResponse result = credentialService.updateCredential(id, request, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(result, "Credential profile updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCredential(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest servletRequest
    ) {
        credentialService.deleteCredential(id, currentUser, servletRequest);
        return ResponseEntity.ok(ApiResponse.ok(null, "Credential profile deleted successfully"));
    }
}
