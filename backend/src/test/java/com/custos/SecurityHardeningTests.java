package com.custos;

import com.custos.modules.auth.dto.LoginRequest;
import com.custos.modules.auth.dto.LoginResponse;
import com.custos.modules.auth.dto.RefreshTokenRequest;
import com.custos.modules.auth.entity.Role;
import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.repository.RoleRepository;
import com.custos.modules.auth.repository.UserRepository;
import com.custos.modules.auth.service.AuthService;
import com.custos.modules.execution.ProcessSanitizer;
import com.custos.modules.vault.service.VaultService;
import com.custos.shared.UnauthorizedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@SpringBootTest
@ActiveProfiles("test")
public class SecurityHardeningTests {

    @Autowired
    private ProcessSanitizer processSanitizer;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private VaultService vaultService;

    @Test
    @DisplayName("Security Hardening - Reject non-whitelisted executables")
    public void testExecutableWhitelistRejection() {
        List<String> dangerousBinaries = List.of(
                "sh", "bash", "cmd.exe", "powershell.exe", "python", "curl", "wget", "nc", "rm"
        );

        for (String binary : dangerousBinaries) {
            Assertions.assertThrows(
                    SecurityException.class,
                    () -> processSanitizer.validateExecutable(binary),
                    "Expected SecurityException when executing: " + binary
            );
        }
    }

    @Test
    @DisplayName("Security Hardening - Reject shell command injection metacharacters")
    public void testCommandInjectionArgumentRejection() {
        List<String> injectionPayloads = List.of(
                "test; rm -rf /",
                "test && cat /etc/passwd",
                "test | bash",
                "test || echo pwned",
                "$(whoami)",
                "`id`",
                "test\nrm -rf /",
                "test\r\necho exploit",
                "> /tmp/overwritten.txt",
                "< /dev/urandom"
        );

        for (String payload : injectionPayloads) {
            Assertions.assertThrows(
                    SecurityException.class,
                    () -> processSanitizer.validateArgument(payload),
                    "Expected SecurityException for payload: " + payload
            );
        }
    }

    @Test
    @DisplayName("Security Hardening - Reject SQL injection in database identifiers")
    public void testSqlIdentifierSanitization() {
        List<String> sqlInjections = List.of(
                "db; DROP TABLE users;",
                "db' OR '1'='1",
                "db\" UNION SELECT * FROM users --",
                "db--comment",
                "db/*comment*/"
        );

        for (String sqlPayload : sqlInjections) {
            Assertions.assertThrows(
                    SecurityException.class,
                    () -> processSanitizer.validateIdentifier(sqlPayload, "databaseName"),
                    "Expected SecurityException for SQL injection payload: " + sqlPayload
            );
        }

        // Valid identifier should not throw
        Assertions.assertDoesNotThrow(() -> {
            processSanitizer.validateIdentifier("production_db_2026", "databaseName");
        });
    }

    @Test
    @DisplayName("Security Hardening - Reject path traversal sequences")
    public void testPathTraversalRejection() {
        List<String> traversalPaths = List.of(
                "../../etc/shadow",
                "..\\..\\Windows\\System32\\config\\SAM",
                "/var/backups/../../../etc/passwd"
        );

        for (String path : traversalPaths) {
            Assertions.assertThrows(
                    SecurityException.class,
                    () -> processSanitizer.validatePath(path),
                    "Expected SecurityException for path traversal: " + path
            );
        }
    }

    @Test
    @DisplayName("Security Hardening - Account Lockout after 5 failed login attempts")
    public void testAccountLockoutAfterMaxFailedAttempts() {
        String testUsername = "lockout_victim_" + UUID.randomUUID().toString().substring(0, 8);
        Role role = roleRepository.findByName("ROLE_OPERATOR").orElse(null);

        User user = User.builder()
                .username(testUsername)
                .email(testUsername + "@custos.io")
                .passwordHash(passwordEncoder.encode("ValidSecret@123"))
                .status(UserStatus.ACTIVE)
                .roles(role != null ? Set.of(role) : Set.of())
                .failedLoginAttempts(0)
                .build();
        userRepository.save(user);

        LoginRequest wrongRequest = new LoginRequest();
        wrongRequest.setUsername(testUsername);
        wrongRequest.setPassword("WrongPassword@999");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        // 4 failed attempts should throw BadCredentialsException
        for (int i = 1; i <= 4; i++) {
            Assertions.assertThrows(BadCredentialsException.class, () -> {
                authService.login(wrongRequest, servletRequest);
            });
        }

        // 5th failed attempt: reaches maxFailedAttempts (5) and triggers LockedException immediately
        Assertions.assertThrows(LockedException.class, () -> {
            authService.login(wrongRequest, servletRequest);
        });

        // 6th attempt: Account is now locked, should throw LockedException
        Assertions.assertThrows(LockedException.class, () -> {
            authService.login(wrongRequest, servletRequest);
        });

        // Even with correct password, locked account cannot login
        LoginRequest correctRequest = new LoginRequest();
        correctRequest.setUsername(testUsername);
        correctRequest.setPassword("ValidSecret@123");

        Assertions.assertThrows(LockedException.class, () -> {
            authService.login(correctRequest, servletRequest);
        });
    }

    @Test
    @DisplayName("Security Hardening - Token Revocation upon user logout")
    public void testTokenRevocationOnLogout() {
        String testUsername = "logout_user_" + UUID.randomUUID().toString().substring(0, 8);
        Role role = roleRepository.findByName("ROLE_VIEWER").orElse(null);

        User user = User.builder()
                .username(testUsername)
                .email(testUsername + "@custos.io")
                .passwordHash(passwordEncoder.encode("ValidSecret@123"))
                .status(UserStatus.ACTIVE)
                .roles(role != null ? Set.of(role) : Set.of())
                .failedLoginAttempts(0)
                .build();
        user = userRepository.save(user);

        // 1. Successful Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(testUsername);
        loginRequest.setPassword("ValidSecret@123");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        LoginResponse loginResponse = authService.login(loginRequest, servletRequest);
        String refreshToken = loginResponse.getRefreshToken();
        Assertions.assertNotNull(refreshToken);

        // 2. Refresh token works initially
        RefreshTokenRequest refreshReq = new RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);
        LoginResponse refreshed = authService.refreshToken(refreshReq);
        String newRefreshToken = refreshed.getRefreshToken();
        Assertions.assertNotNull(newRefreshToken);

        // 3. User logs out
        authService.logout(user.getId(), servletRequest);

        // 4. Using the revoked token now must throw UnauthorizedException
        RefreshTokenRequest postLogoutReq = new RefreshTokenRequest();
        postLogoutReq.setRefreshToken(newRefreshToken);
        Assertions.assertThrows(UnauthorizedException.class, () -> {
            authService.refreshToken(postLogoutReq);
        });
    }

    @Test
    @DisplayName("Security Hardening - Vault ciphertext tamper proof (AES-GCM Auth Tag Mismatch)")
    public void testVaultCiphertextTamperProof() {
        String sensitiveData = "SuperSecretDatabasePassword_2026!";
        String encrypted = vaultService.encrypt(sensitiveData);
        Assertions.assertNotNull(encrypted);

        // Tamper with the ciphertext (flip a character in the Base64 payload)
        char[] chars = encrypted.toCharArray();
        chars[chars.length - 2] = (chars[chars.length - 2] == 'A') ? 'B' : 'A';
        String tamperedCiphertext = new String(chars);

        // AES-256-GCM authentication tag verification must fail and throw SecurityException
        Assertions.assertThrows(SecurityException.class, () -> {
            vaultService.decrypt(tamperedCiphertext);
        });
    }
}
