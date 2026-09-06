package com.custos.modules.auth.controller;

import com.custos.modules.auth.dto.*;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.UserService;
import com.custos.shared.ApiResponse;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {

        Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        String property = sort[0];
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, property));

        Page<UserResponse> users = userService.getUsers(search, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        UserResponse user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserResponse user = userService.createUser(request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(user, "สร้างผู้ใช้งานสำเร็จ"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserResponse user = userService.updateUser(id, request, currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(user, "แก้ไขข้อมูลผู้ใช้งานสำเร็จ"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserResponse user = userService.updateStatus(id, request.getStatus(), currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(user, "ปรับเปลี่ยนสถานะผู้ใช้งานสำเร็จ"));
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userService.resetPassword(id, request.getNewPassword(), currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null, "รีเซ็ตรหัสผ่านสำเร็จ"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        userService.deleteUser(id, currentUser.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(null, "ลบผู้ใช้งานสำเร็จ"));
    }
}
