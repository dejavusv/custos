package com.custos.modules.auth.service;

import com.custos.modules.auth.dto.CreateUserRequest;
import com.custos.modules.auth.dto.UpdateUserRequest;
import com.custos.modules.auth.dto.UserResponse;
import com.custos.modules.auth.entity.Role;
import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.repository.RoleRepository;
import com.custos.modules.auth.repository.UserRepository;
import com.custos.shared.BadRequestException;
import com.custos.shared.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(String query, UserStatus status, Pageable pageable) {
        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean hasStatus = status != null;

        Page<User> users;
        if (hasQuery && hasStatus) {
            users = userRepository.searchByQueryAndStatus(query.trim(), status, pageable);
        } else if (hasQuery) {
            users = userRepository.searchByQuery(query.trim(), pageable);
        } else if (hasStatus) {
            users = userRepository.findByStatus(status, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(UserResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบผู้ใช้งาน ID: " + id));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, String createdBy) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username '" + request.getUsername() + "' มีผู้ใช้งานแล้วในระบบ");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' มีผู้ใช้งานแล้วในระบบ");
        }

        Set<Role> roles = new HashSet<>();
        for (String roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new BadRequestException("ไม่พบ Role: " + roleName));
            roles.add(role);
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .roles(roles)
                .build();
        user.setCreatedBy(createdBy);

        User savedUser = userRepository.save(user);

        auditLogService.logAction(savedUser.getId(), createdBy, "USER_CREATED", "USER", null, 
                "Created user: " + savedUser.getUsername() + " with roles: " + request.getRoles());

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบผู้ใช้งาน ID: " + id));

        if (!user.getEmail().equalsIgnoreCase(request.getEmail()) && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' มีผู้ใช้งานแล้วในระบบ");
        }

        Set<Role> roles = new HashSet<>();
        for (String roleName : request.getRoles()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new BadRequestException("ไม่พบ Role: " + roleName));
            roles.add(role);
        }

        user.setEmail(request.getEmail());
        user.setRoles(roles);

        User savedUser = userRepository.save(user);

        auditLogService.logAction(savedUser.getId(), updatedBy, "USER_UPDATED", "USER", null, 
                "Updated user: " + savedUser.getUsername());

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatus newStatus, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบผู้ใช้งาน ID: " + id));

        user.setStatus(newStatus);
        if (newStatus == UserStatus.ACTIVE) {
            user.setLockoutUntil(null);
            user.setFailedLoginAttempts(0);
        }

        User savedUser = userRepository.save(user);

        auditLogService.logAction(savedUser.getId(), updatedBy, "USER_STATUS_CHANGED", "USER", null, 
                "Changed status of user " + user.getUsername() + " to " + newStatus);

        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public void resetPassword(UUID id, String newPassword, String updatedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบผู้ใช้งาน ID: " + id));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLockoutUntil(null);
        user.setFailedLoginAttempts(0);
        if (user.getStatus() == UserStatus.LOCKED) {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);

        auditLogService.logAction(user.getId(), updatedBy, "PASSWORD_RESET", "USER", null, 
                "Password reset for user: " + user.getUsername());
    }

    @Transactional
    public void deleteUser(UUID id, String deletedBy) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบผู้ใช้งาน ID: " + id));

        if ("admin".equalsIgnoreCase(user.getUsername())) {
            throw new BadRequestException("ไม่สามารถลบบัญชี Super Admin หลักของระบบได้");
        }

        userRepository.delete(user);

        auditLogService.logAction(id, deletedBy, "USER_DELETED", "USER", null, 
                "Deleted user: " + user.getUsername());
    }
}
