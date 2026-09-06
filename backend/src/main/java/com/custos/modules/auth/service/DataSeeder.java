package com.custos.modules.auth.service;

import com.custos.modules.auth.entity.Role;
import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.repository.RoleRepository;
import com.custos.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // สร้าง Role พื้นฐานหากยังไม่มี
        createRoleIfNotFound("role_super_admin", "ROLE_SUPER_ADMIN", "ผู้ดูแลระบบสูงสุด สิทธิ์เต็มทุกส่วน");
        createRoleIfNotFound("role_admin", "ROLE_ADMIN", "ผู้ดูแลระบบทั่วไป จัดการ Task และ Pipeline");
        createRoleIfNotFound("role_operator", "ROLE_OPERATOR", "เจ้าหน้าที่ปฏิบัติการ สั่งรันงานและดู Log");
        createRoleIfNotFound("role_viewer", "ROLE_VIEWER", "ผู้เข้าชม ดู Dashboard และรายงานผล");

        // ตรวจสอบและสร้าง/อัปเดตบัญชี Super Admin เริ่มต้น
        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_SUPER_ADMIN not found"));

        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    // หาก hash ยังไม่ตรงกับรหัสผ่านเริ่มต้น ให้ทำการซิงค์อัปเดตรหัสผ่านให้ถูกต้อง
                    if (!passwordEncoder.matches("AdminPassword@123", admin.getPasswordHash())) {
                        admin.setPasswordHash(passwordEncoder.encode("AdminPassword@123"));
                        admin.setStatus(UserStatus.ACTIVE);
                        admin.setFailedLoginAttempts(0);
                        admin.setLockoutUntil(null);
                        userRepository.save(admin);
                        log.info("Default Super Admin password synchronized: username=admin, password=AdminPassword@123");
                    }
                },
                () -> {
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@custos.local")
                            .passwordHash(passwordEncoder.encode("AdminPassword@123"))
                            .status(UserStatus.ACTIVE)
                            .failedLoginAttempts(0)
                            .roles(Set.of(superAdminRole))
                            .build();
                    admin.setCreatedBy("SYSTEM");

                    userRepository.save(admin);
                    log.info("Default Super Admin account initialized: username=admin, password=AdminPassword@123");
                }
        );
    }

    private void createRoleIfNotFound(String id, String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.builder().id(id).name(name).description(description).build());
        }
    }
}
