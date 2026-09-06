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
    @DisplayName("ทดสอบ DataSeeder อัปเดตรหัสผ่านเมื่อ hash ในฐานข้อมูลไม่ตรงกับ default password")
    void testDataSeederSyncsPassword() {
        // จำลองสถานะที่ user admin มี dummy hash ใน DB
        var admin = userRepository.findByUsername("admin").orElseThrow();
        admin.setPasswordHash("$2a$12$dummyHashFromOldFlywaySeedPlaceholder12345678901234567890");
        userRepository.save(admin);

        // ตรวจสอบว่าก่อนรัน DataSeeder รหัสผ่านผิด
        assertFalse(passwordEncoder.matches("AdminPassword@123", admin.getPasswordHash()));

        // รัน DataSeeder
        dataSeeder.run();

        // ตรวจสอบว่าหลังรัน DataSeeder รหัสผ่านถูกซิงค์กลับมาเป็น AdminPassword@123
        var updatedAdmin = userRepository.findByUsername("admin").orElseThrow();
        assertTrue(passwordEncoder.matches("AdminPassword@123", updatedAdmin.getPasswordHash()));
        assertEquals(com.custos.modules.auth.entity.UserStatus.ACTIVE, updatedAdmin.getStatus());

        // และสามารถล็อกอินได้สำเร็จ
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("AdminPassword@123");
        LoginResponse response = authService.login(request, new MockHttpServletRequest());
        assertNotNull(response.getAccessToken());
    }
}
