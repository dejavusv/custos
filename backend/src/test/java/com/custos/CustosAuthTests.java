package com.custos;

import com.custos.modules.auth.dto.LoginRequest;
import com.custos.modules.auth.dto.LoginResponse;
import com.custos.modules.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CustosAuthTests {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("ทดสอบ Login สำเร็จด้วย Default Super Admin")
    void testSuccessfulLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("AdminPassword@123");

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        LoginResponse response = authService.login(request, mockRequest);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertNotNull(response.getRefreshToken());
        assertEquals("admin", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_SUPER_ADMIN"));
    }

    @Test
    @DisplayName("ทดสอบ Login ล้มเหลวเมื่อรหัสผ่านผิด")
    void testFailedLoginWithWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("WrongPassword@999");

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        assertThrows(BadCredentialsException.class, () -> authService.login(request, mockRequest));
    }

    @Test
    @DisplayName("ทดสอบ PasswordEncoder เข้ารหัสและตรวจสอบรหัสผ่านตรงกัน")
    void testPasswordEncoder() {
        String raw = "SecureP@ssw0rd2026";
        String encoded = passwordEncoder.encode(raw);

        assertTrue(encoded.startsWith("$2a$12$"));
        assertTrue(passwordEncoder.matches(raw, encoded));
        assertFalse(passwordEncoder.matches("WrongP@ss", encoded));
    }

    @Autowired
    private com.custos.modules.auth.repository.UserRepository userRepository;

    @Autowired
    private com.custos.modules.auth.service.DataSeeder dataSeeder;

    @Test
    @DisplayName("ทดสอบ DataSeeder ตั้งรหัสผ่านเริ่มต้นให้ admin ที่ยังเป็นแถว seed จาก migration")
    void testDataSeederInitializesMigrationSeededAdmin() {
        // จำลองสถานะหลังรัน V2 migration: admin มี hash placeholder ที่ล็อกอินไม่ได้
        var admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setPasswordHash(com.custos.modules.auth.service.DataSeeder.MIGRATION_PLACEHOLDER_HASH);
        userRepository.save(admin);

        dataSeeder.run();

        var updatedAdmin = userRepository.findByUsername("admin").orElseThrow();
        assertTrue(passwordEncoder.matches("AdminPassword@123", updatedAdmin.getPasswordHash()));
        assertEquals(com.custos.modules.auth.entity.UserStatus.ACTIVE, updatedAdmin.getStatus());

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("AdminPassword@123");
        LoginResponse response = authService.login(request, new MockHttpServletRequest());
        assertNotNull(response.getAccessToken());
    }

    @Test
    @DisplayName("ทดสอบ DataSeeder ไม่รีเซ็ตรหัสผ่าน admin ที่เปลี่ยนไปแล้ว")
    void testDataSeederKeepsChangedAdminPassword() {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String originalHash = admin.getPasswordHash();
        String changedHash = passwordEncoder.encode("MyNewSecret#2026");
        admin.setPasswordHash(changedHash);
        userRepository.save(admin);

        try {
            dataSeeder.run();

            var after = userRepository.findByUsername("admin").orElseThrow();
            assertEquals(changedHash, after.getPasswordHash());
            assertFalse(passwordEncoder.matches("AdminPassword@123", after.getPasswordHash()));
        } finally {
            // คืนค่าเดิมเพื่อไม่ให้กระทบ test อื่นที่ล็อกอินด้วยรหัสเริ่มต้น
            var restore = userRepository.findByUsername("admin").orElseThrow();
            restore.setPasswordHash(originalHash);
            userRepository.save(restore);
        }
    }

    @Test
    @DisplayName("ทดสอบ DataSeeder ไม่ initialize admin เมื่อไม่ได้ตั้ง CUSTOS_ADMIN_PASSWORD")
    void testDataSeederSkipsAdminWhenPasswordNotConfigured() {
        var admin = userRepository.findByUsername("admin").orElseThrow();
        String originalHash = admin.getPasswordHash();
        admin.setPasswordHash(com.custos.modules.auth.service.DataSeeder.MIGRATION_PLACEHOLDER_HASH);
        userRepository.save(admin);
        Object configured = org.springframework.test.util.ReflectionTestUtils.getField(dataSeeder, "adminPassword");

        try {
            org.springframework.test.util.ReflectionTestUtils.setField(dataSeeder, "adminPassword", "");
            dataSeeder.run();

            var after = userRepository.findByUsername("admin").orElseThrow();
            assertEquals(com.custos.modules.auth.service.DataSeeder.MIGRATION_PLACEHOLDER_HASH, after.getPasswordHash());
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(dataSeeder, "adminPassword", configured);
            var restore = userRepository.findByUsername("admin").orElseThrow();
            restore.setPasswordHash(originalHash);
            userRepository.save(restore);
        }
    }
}
