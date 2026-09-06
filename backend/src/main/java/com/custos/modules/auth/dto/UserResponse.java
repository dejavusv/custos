package com.custos.modules.auth.dto;

import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String username;
    private String email;
    private UserStatus status;
    private int failedLoginAttempts;
    private Instant lockoutUntil;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private List<String> roles;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .status(user.getStatus())
                .failedLoginAttempts(user.getFailedLoginAttempts())
                .lockoutUntil(user.getLockoutUntil())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .roles(user.getRoles().stream().map(r -> r.getName()).collect(Collectors.toList()))
                .build();
    }
}
