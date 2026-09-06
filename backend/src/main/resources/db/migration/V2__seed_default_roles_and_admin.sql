-- V2: Seed ข้อมูลสิทธิ์เริ่มต้น (Master Roles) และบัญชี Super Admin เริ่มต้น

INSERT INTO roles (id, name, description) VALUES
('role_super_admin', 'ROLE_SUPER_ADMIN', 'ผู้ดูแลระบบสูงสุด สิทธิ์เต็มทุกส่วน รวมถึงจัดการผู้ใช้และ Credentials'),
('role_admin', 'ROLE_ADMIN', 'ผู้ดูแลระบบทั่วไป จัดการ Task, Pipeline, Schedules และดู Audit Logs'),
('role_operator', 'ROLE_OPERATOR', 'เจ้าหน้าที่ปฏิบัติการ สั่ง Trigger Run/Pause Task และดู Execution Log'),
('role_viewer', 'ROLE_VIEWER', 'ผู้เข้าชม ดู Dashboard และรายงานผลได้อย่างเดียว')
ON CONFLICT (id) DO NOTHING;

-- บัญชี Super Admin เริ่มต้น:
-- Username: admin
-- Email: admin@custos.local
-- Password: AdminPassword@123
-- BCrypt Hash (Cost 12): $2a$12$1j3bE8b0rCkyjWvFfGz5hO9k5b4c3d2e1f0g9h8i7j6k5l4m3n2o1 (ตัวอย่าง) หรืออัปเดตผ่าน Java DataSeeder
INSERT INTO users (
    id, username, email, password_hash, status, failed_login_attempts, created_at, updated_at, created_by
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'admin',
    'admin@custos.local',
    '$2a$12$r81ZfDqZg2g8J6X.p1Iu0.m5lB9x5w5w0jQZfO8hU5J4mF1Z8tH1W',
    'ACTIVE',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'SYSTEM'
)
ON CONFLICT (username) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
('a0000000-0000-0000-0000-000000000001', 'role_super_admin')
ON CONFLICT (user_id, role_id) DO NOTHING;
