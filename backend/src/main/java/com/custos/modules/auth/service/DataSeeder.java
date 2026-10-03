package com.custos.modules.auth.service;

import com.custos.modules.auth.entity.Role;
import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.entity.UserStatus;
import com.custos.modules.auth.repository.RoleRepository;
import com.custos.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // password_hash ที่ V2__seed_default_roles_and_admin.sql ใส่ไว้ (ใช้ล็อกอินไม่ได้) ห้ามแก้ค่านี้
    public static final String MIGRATION_PLACEHOLDER_HASH = "$2a$12$r81ZfDqZg2g8J6X.p1Iu0.m5lB9x5w5w0jQZfO8hU5J4mF1Z8tH1W";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // รหัสผ่านเริ่มต้นของ admin มาจาก env CUSTOS_ADMIN_PASSWORD (prod ไม่มีค่า default)
    @Value("${custos.auth.admin-password:}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // สร้าง Role พื้นฐานหากยังไม่มี
        createRoleIfNotFound("role_super_admin", "ROLE_SUPER_ADMIN", "ผู้ดูแลระบบสูงสุด สิทธิ์เต็มทุกส่วน");
        createRoleIfNotFound("role_admin", "ROLE_ADMIN", "ผู้ดูแลระบบทั่วไป จัดการ Task และ Pipeline");
        createRoleIfNotFound("role_operator", "ROLE_OPERATOR", "เจ้าหน้าที่ปฏิบัติการ สั่งรันงานและดู Log");
        createRoleIfNotFound("role_viewer", "ROLE_VIEWER", "ผู้เข้าชม ดู Dashboard และรายงานผล");

        // สร้างบัญชี Super Admin เริ่มต้นเฉพาะตอนยังไม่มี admin (หรือยังเป็นแถว seed จาก V2 ที่ยังไม่เคยตั้งรหัสผ่าน)
        // หลังจากนั้นจะไม่แตะรหัสผ่านของ admin อีก เพื่อให้เปลี่ยนรหัสผ่านแล้วไม่ถูกรีเซ็ตทุกครั้งที่ restart
        Role superAdminRole = roleRepository.findByName("ROLE_SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("ROLE_SUPER_ADMIN not found"));

        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("CUSTOS_ADMIN_PASSWORD is not set: the default Super Admin is NOT initialized. "
                    + "Set it before the first start to be able to log in as 'admin'.");
            return;
        }

        userRepository.findByUsername("admin").ifPresentOrElse(
                admin -> {
                    // V2 migration ใส่ admin พร้อม hash placeholder ที่ล็อกอินไม่ได้ ต้องตั้งรหัสผ่านเริ่มต้นให้ครั้งแรกเท่านั้น
                    if (MIGRATION_PLACEHOLDER_HASH.equals(admin.getPasswordHash())) {
                        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                        admin.setStatus(UserStatus.ACTIVE);
                        admin.setFailedLoginAttempts(0);
                        admin.setLockoutUntil(null);
                        userRepository.save(admin);
                        log.info("Default Super Admin initialized: username=admin (change the password after first login)");
                    }
                },
                () -> {
                    User admin = User.builder()
                            .username("admin")
                            .email("admin@custos.local")
                            .passwordHash(passwordEncoder.encode(adminPassword))
                            .status(UserStatus.ACTIVE)
                            .failedLoginAttempts(0)
                            .roles(Set.of(superAdminRole))
                            .build();
                    admin.setCreatedBy("SYSTEM");

                    userRepository.save(admin);
                    log.info("Default Super Admin created: username=admin (change the password after first login)");
                }
        );
    }

    private void createRoleIfNotFound(String id, String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(Role.builder().id(id).name(name).description(description).build());
        }
    }
}
