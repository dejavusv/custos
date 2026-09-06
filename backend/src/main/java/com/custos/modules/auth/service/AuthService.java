package com.custos.modules.auth.service;

import com.custos.modules.auth.dto.LoginRequest;
import com.custos.modules.auth.dto.LoginResponse;
import com.custos.modules.auth.dto.RefreshTokenRequest;
import com.custos.modules.auth.entity.RefreshToken;
import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.repository.RefreshTokenRepository;
import com.custos.modules.auth.repository.UserRepository;
import com.custos.modules.auth.security.JwtTokenProvider;
import com.custos.modules.auth.security.UserPrincipal;
import com.custos.shared.BadRequestException;
import com.custos.shared.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuditLogService auditLogService;

    @Value("${custos.auth.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${custos.auth.lockout-duration-minutes:15}")
    private int lockoutDurationMinutes;

    @Value("${custos.jwt.refresh-token-expiration-ms:604800000}")
    private long refreshTokenExpirationMs;

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername())
                .or(() -> userRepository.findByEmail(request.getUsername()))
                .orElseThrow(() -> {
                    auditLogService.logFromRequest(null, request.getUsername(), "LOGIN_FAILED", "AUTH", "User not found", httpRequest);
                    return new BadCredentialsException("ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
                });

        // ตรวจสอบ Account Lockout
        if (user.getLockoutUntil() != null) {
            if (Instant.now().isBefore(user.getLockoutUntil())) {
                long remainingMinutes = ChronoUnit.MINUTES.between(Instant.now(), user.getLockoutUntil()) + 1;
                auditLogService.logFromRequest(user.getId(), user.getUsername(), "LOGIN_BLOCKED", "AUTH", "Account temporarily locked", httpRequest);
                throw new LockedException("บัญชีถูกระงับชั่วคราวเนื่องจากรหัสผ่านผิดเกินกำหนด กรุณารออีก " + remainingMinutes + " นาที");
            } else {
                // หมดระยะเวลา Lockout แล้ว ปลดล็อกอัตโนมัติ
                user.setLockoutUntil(null);
                user.setFailedLoginAttempts(0);
                if (user.getStatus() == UserStatus.LOCKED) {
                    user.setStatus(UserStatus.ACTIVE);
                }
            }
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            auditLogService.logFromRequest(user.getId(), user.getUsername(), "LOGIN_BLOCKED", "AUTH", "Account is suspended", httpRequest);
            throw new LockedException("บัญชีนี้ถูกระงับการใช้งานโดยผู้ดูแลระบบ กรุณาติดต่อ Admin");
        }

        // ตรวจสอบรหัสผ่าน
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= maxFailedAttempts) {
                user.setLockoutUntil(Instant.now().plus(lockoutDurationMinutes, ChronoUnit.MINUTES));
                user.setStatus(UserStatus.LOCKED);
                userRepository.save(user);

                auditLogService.logFromRequest(user.getId(), user.getUsername(), "ACCOUNT_LOCKED", "AUTH", 
                        "Account locked due to " + attempts + " consecutive failed attempts", httpRequest);
                throw new LockedException("คุณระบุรหัสผ่านผิดติดต่อกันเกิน 5 ครั้ง บัญชีถูกระงับการใช้งาน 15 นาที");
            }

            userRepository.save(user);
            auditLogService.logFromRequest(user.getId(), user.getUsername(), "LOGIN_FAILED", "AUTH", 
                    "Invalid password (attempt " + attempts + "/" + maxFailedAttempts + ")", httpRequest);
            throw new BadCredentialsException("ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง");
        }

        // ล็อกอินสำเร็จ -> รีเซ็ต Failed attempts
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateAccessToken(userPrincipal);
        String refreshTokenStr = tokenProvider.generateRefreshToken(user.getId());

        // บันทึก Refresh Token
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        auditLogService.logFromRequest(user.getId(), user.getUsername(), "LOGIN_SUCCESS", "AUTH", "User successfully authenticated", httpRequest);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
                .build();
    }

    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.getRefreshToken();
        if (!tokenProvider.validateToken(tokenStr)) {
            throw new UnauthorizedException("Refresh Token ไม่ถูกต้องหรือหมดอายุ");
        }

        RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new UnauthorizedException("ไม่พบ Refresh Token ในระบบ"));

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new UnauthorizedException("Refresh Token ถูกยกเลิกหรือหมดอายุแล้ว กรุณาเข้าสู่ระบบใหม่");
        }

        User user = refreshToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UnauthorizedException("บัญชีผู้ใช้ไม่ได้อยู่ในสถานะใช้งาน");
        }

        // Token Rotation: ยกเลิกตัวเดิม และออกตัวใหม่
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(userPrincipal);
        String newRefreshTokenStr = tokenProvider.generateRefreshToken(user.getId());

        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(newRefreshTokenStr)
                .expiresAt(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(r -> r.getName()).toList())
                .build();
    }

    @Transactional
    public void logout(UUID userId, HttpServletRequest httpRequest) {
        userRepository.findById(userId).ifPresent(user -> {
            refreshTokenRepository.revokeAllUserTokens(user);
            auditLogService.logFromRequest(user.getId(), user.getUsername(), "LOGOUT", "AUTH", "User logged out", httpRequest);
        });
    }
}
