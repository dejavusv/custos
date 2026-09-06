package com.custos.modules.auth.controller;

import com.custos.modules.auth.dto.LoginRequest;
import com.custos.modules.auth.dto.LoginResponse;
import com.custos.modules.auth.dto.RefreshTokenRequest;
import com.custos.modules.auth.dto.UserResponse;
import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.auth.service.AuthService;
import com.custos.modules.auth.service.UserService;
import com.custos.shared.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok(response, "เข้าสู่ระบบสำเร็จ"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "ต่ออายุ Token สำเร็จ"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserPrincipal currentUser,
            HttpServletRequest httpRequest) {
        if (currentUser != null) {
            authService.logout(currentUser.getId(), httpRequest);
        }
        return ResponseEntity.ok(ApiResponse.ok(null, "ออกจากระบบสำเร็จ"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        UserResponse response = userService.getUserById(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
