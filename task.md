# รายการและลำดับขั้นตอนการพัฒนาระบบ (Development Tasks Breakdown)

เอกสารฉบับนี้แสดงลำดับขั้นตอนการพัฒนาระบบ **Server Automation & Task Scheduling Platform (Custos)** แบบเป็นขั้นเป็นตอน (Sequential Checklist) พร้อมระบุระดับความสำคัญ (Priority) และเงื่อนไขก่อนหน้า (Prerequisites) เพื่อใช้ติดตามความคืบหน้าของโครงการ

---

## สัญลักษณ์และเกณฑ์ความสำคัญ (Priority Legend)
* **[P0] Blocker / Critical:** งานโครงสร้างพื้นฐานและความปลอดภัย จำเป็นต้องทำก่อน
* **[P1] High / Core Feature:** ฟังก์ชันหลักของระบบตาม Business Logic
* **[P2] Medium / Enhancement:** ฟังก์ชันเสริม การปรับปรุงความเสถียร หรือ UI/UX

---

## หมวดที่ 1: การวางรากฐานและโครงสร้างระบบ (Project Setup & Infrastructure)

- [x] **TASK-101 [P0]** วางโครงสร้างไดเรกทอรีโปรเจกต์แบบ Monorepo/Decoupled (`backend/`, `frontend/`, `docker/`, `docs/`)
- [x] **TASK-102 [P0]** สร้าง Backend Skeleton ด้วย Spring Boot 3.3.x (Java 21), Maven/Gradle พร้อม Dependencies พื้นฐาน (JPA, Security, Validation, Lombok, Actuator)
- [x] **TASK-103 [P0]** สร้าง Frontend Skeleton ด้วย Vite + React + TypeScript + Tailwind CSS พร้อมติดตั้ง `shadcn/ui` และ `lucide-react`
- [x] **TASK-104 [P0]** ตั้งค่า `docker/docker-compose.dev.yml` สำหรับ Local Development:
  - PostgreSQL 16 (พอร์ต 5432)
  - Redis 7 (พอร์ต 6379)
  - Mock SFTP Server (OpenSSH/SFTP test container)
- [x] **TASK-105 [P0]** กำหนดค่า Base Entity (`id`, `created_at`, `updated_at`, `created_by`) และ Spring Data JPA Auditing

---

## หมวดที่ 2: ระบบความปลอดภัยและการจัดการผู้ใช้ (Auth, Security & User Management)
*Prerequisite: หมวดที่ 1*

- [x] **TASK-201 [P0]** เขียน Flyway Migration `V1__init_auth_schema.sql` (ตาราง `users`, `roles`, `user_roles`, `audit_logs`)
- [x] **TASK-202 [P0]** เขียน Seed Data สำหรับ Master Roles (`ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`) และบัญชี Super Admin เริ่มต้น
- [x] **TASK-203 [P0]** พัฒนาระบบ Password Hashing ด้วย **BCrypt (Cost Factor = 12)**
- [x] **TASK-204 [P0]** พัฒนาระบบ Stateless JWT Authentication:
  - Access Token Generator (อายุ 15-30 นาที)
  - Refresh Token Rotation เก็บใน Database/Redis พร้อมระบบ Revoke
  - JwtAuthenticationFilter ดักจับทุก Request
- [x] **TASK-205 [P1]** พัฒนาระบบ Account Lockout:
  - บันทึกจำนวนครั้งที่ล็อกอินผิดพลาด (Failed Attempt Counter)
  - บล็อกบัญชีอัตโนมัติ 15 นาทีเมื่อใส่รหัสผิดเกิน 5 ครั้ง
- [x] **TASK-206 [P1]** พัฒนาระบบ Audit Trail Interceptor: บันทึก IP, User, Action, Timestamp ลงตาราง `audit_logs`
- [x] **TASK-207 [P1]** พัฒนา REST APIs สำหรับ User Management:
  - `POST /api/v1/auth/login` และ `POST /api/v1/auth/refresh`
  - `GET /api/v1/users` (พร้อม Filter, Pagination, Sort)
  - `POST /api/v1/users`, `PUT /api/v1/users/{id}`, `DELETE /api/v1/users/{id}`
  - `PUT /api/v1/users/{id}/status` (Suspend / Active)
  - `POST /api/v1/users/{id}/reset-password`
- [x] **TASK-208 [P1]** พัฒนาหน้าจอ Frontend Auth & User Management:
  - หน้า Login Screen พร้อม Validation ด้วย React Hook Form + Zod
  - Axios Interceptor จัดการ Refresh Token อัตโนมัติเมื่อ Access Token หมดอายุ
  - User Store (Zustand) จัดการ Auth State
  - หน้า User Management Data Grid (TanStack Table) พร้อม Modal เพิ่ม/แก้ไข/รีเซ็ตรหัสผ่าน

---

## หมวดที่ 3: ระบบจัดเก็บความลับและ Sandboxed CLI Execution (Vault & Process Engine)
*Prerequisite: หมวดที่ 2*

- [x] **TASK-301 [P0]** เขียน Flyway Migration `V3__init_vault_schema.sql` (ตาราง `credentials_vault`)
- [x] **TASK-302 [P0]** พัฒนา `VaultService`:
  - เข้ารหัสและถอดรหัสข้อมูลด้วย **AES-256-GCM**
  - ดึง Master Vault Key จาก Environment Variable (`CUSTOS_VAULT_MASTER_KEY`)
- [x] **TASK-303 [P0]** พัฒนา Sandboxed `ProcessExecutorService`:
  - ใช้ Java `ProcessBuilder` ทำงานภายใต้ Thread Pool ควบคุม
  - กลไก **Argument Whitelisting & Input Sanitizer** ป้องกัน Command Injection โดยเด็ดขาด
  - มี Watchdog Monitor กำหนด Execution Timeout และ Graceful Kill / Forcible Termination
  - สตรีม `stdout` และ `stderr` เข้า Ring Buffer เพื่อส่งต่อ WebSocket
- [x] **TASK-304 [P1]** พัฒนา REST APIs จัดการ Credential Profiles (Database Credentials, FTP/SFTP Profiles)

---

## หมวดที่ 4: เครื่องยนต์สำรองข้อมูล (Database & File Backup Engines)
*Prerequisite: หมวดที่ 3*

- [x] **TASK-401 [P1]** พัฒนา Database Backup Engine สำหรับ **MySQL & MariaDB**:
  - สร้าง Command Array สำหรับ `mysqldump` ตาม Argument Whitelist
  - สตรีม Dump Output ตรงเข้า `GZIPOutputStream` หรือ ZSTD โดยไม่เก็บไฟล์ดิบและไม่ใช้ RAM สูง
  - รองรับการเลือกทั้งฐานข้อมูลหรือระบุเฉพาะรายชื่อ Tables
- [x] **TASK-402 [P1]** พัฒนา Database Backup Engine สำหรับ **PostgreSQL**:
  - สร้าง Command Array สำหรับ `pg_dump`
  - สตรีม Dump Output เข้า Compression Stream แบบ Zero-RAM เช่นเดียวกัน
- [x] **TASK-403 [P1]** พัฒนา File & Directory Backup Engine:
  - บีบอัดไดเรกทอรีเป็น `.tar.gz`, `.tar.zst`, `.zip`
  - ฟังก์ชัน Exclusion Filtering (ยกเว้นไฟล์/โฟลเดอร์ตาม Glob Pattern เช่น `node_modules`, `*.log`, `temp/*`)
- [x] **TASK-404 [P1]** พัฒนาระบบ Retention Policy Cleanup:
  - ตรวจสอบไฟล์สำรองย้อนหลังตามจำนวนวันที่กำหนด (Retention Days)
  - ลบไฟล์สำรองเก่าที่เกินกำหนดอย่างปลอดภัย พร้อมบันทึก Audit Log

---

## หมวดที่ 5: การจัดเก็บ การแบ่งส่วน และถ่ายโอนไฟล์ (Storage & Split Transfer Engine)
*Prerequisite: หมวดที่ 4*

- [x] **TASK-501 [P1]** พัฒนาระบบ **Storage Pre-check**:
  - ตรวจสอบขนาดพื้นที่ว่างของ Disk ปลายทาง (Local/Remote) ก่อนเริ่มงาน
  - หากพื้นที่ว่างไม่เพียงพอ ให้ Abort งานทันทีก่อนเริ่มประมวลผล
- [x] **TASK-502 [P0]** พัฒนาระบบ **Chunk Splitter Engine**:
  - สตรีมตัดแบ่งไฟล์ขนาดใหญ่ตามขนาดที่กำหนด (เช่น 100MB, 500MB, 1GB)
  - ตั้งชื่อไฟล์ตามลำดับมาตรฐาน (เช่น `backup.tar.gz.part01`, `backup.tar.gz.part02`)
- [x] **TASK-503 [P0]** พัฒนาระบบคำนวณและตรวจสอบ Checksum:
  - สร้างค่า Checksum **SHA-256** สำหรับไฟล์ต้นฉบับและทุก Chunk
  - ตรวจสอบความถูกต้องของ Checksum หลังการถ่ายโอน
- [x] **TASK-504 [P1]** พัฒนา **FTP / FTPS Chunk Uploader** (Apache Commons Net):
  - โหมด Passive, รองรับ TLS/SSL
  - สตรีมส่งข้อมูลทีละ Chunk
- [x] **TASK-505 [P1]** พัฒนา **SFTP Chunk Uploader** (JSch / SSHD):
  - รองรับ Password Authentication และ Private Key Authentication
- [x] **TASK-506 [P0]** พัฒนาระบบ **Resilient Sequential Transfer & Auto-Retry**:
  - ทยอยส่งทีละ Chunk ตามลำดับ
  - หากเกิด Network หลุดระหว่างส่ง ให้ Retry เฉพาะ Chunk ที่ผิดพลาด (ไม่เริ่มใหม่ตั้งแต่ Chunk แรก)

---

## หมวดที่ 6: การตั้งเวลาและร้อยเรียงขั้นตอนงาน (Scheduler & DAG Pipeline Orchestration)
*Prerequisite: หมวดที่ 4, 5*

- [x] **TASK-601 [P0]** เขียน Flyway Migration `V4__init_pipeline_schema.sql` (ตาราง `task_definitions`, `pipeline_definitions`, `pipeline_step_nodes`, `pipeline_executions`, `step_execution_logs` และ Quartz Cluster Tables)
- [x] **TASK-602 [P0]** ผสานและตั้งค่า **Quartz Scheduler**:
  - กำหนด JobStore เป็น PostgreSQL Database สำหรับรองรับ Cluster
  - สร้าง Scheduler API: Trigger Now, Pause, Resume, Cancel/Abort
  - รองรับ Daily, Weekly, Monthly, Timezone-specific และ Advanced Cron Expressions
  - จัดการ Missfire Policy
- [x] **TASK-603 [P0]** พัฒนา **DAG Pipeline Orchestration Engine**:
  - ตรวจสอบวงวนของ Graph (Cycle Detection)
  - การส่งต่อผลลัพธ์ระหว่าง Step (Context Passing เช่น Output Path จาก DB Dump -> Input Path ของ Split Transfer)
  - การแตกแขนงเงื่อนไข (Conditional Branching): `On Success` ดำเนินการต่อ, `On Failure` ไปยัง Error Handler/Rollback
- [x] **TASK-604 [P1]** พัฒนาหน้าจอ Visual Pipeline Builder ด้วย **React Flow (`@xyflow/react`)**:
  - Custom Nodes สำหรับแต่ละ Task (DB Backup, File Backup, Split Transfer, Email Alert)
  - ลากเส้นเชื่อมต่อ Edge กำหนด Success / Failure Path
  - Node Config Sidebar/Modal เชื่อมกับ Zod Form
  - ฟังก์ชันบันทึกและโหลด DAG Graph Layout ผ่าน REST API

---

## หมวดที่ 7: Real-time Live Console และระบบแจ้งเตือน (Live Console & AWS SES)
*Prerequisite: หมวดที่ 6*

- [x] **TASK-701 [P0]** ติดตั้งและตั้งค่า **Spring WebSocket + STOMP Broker**:
  - Channel `/topic/pipeline/{executionId}/logs` สำหรับสตรีม Log
  - Channel `/topic/pipeline/{executionId}/progress` สำหรับสตรีม % Progress
- [x] **TASK-702 [P1]** พัฒนา Frontend **Live Console Terminal**:
  - การเชื่อมต่อ STOMP Client (`@stomp/stompjs`)
  - Terminal Component (Auto-scroll, Pause scroll, ANSI Color Parsing, Search in logs)
  - Chunk Progress Bar แสดงสถานะการส่งไฟล์แบบ Real-time
- [x] **TASK-703 [P1]** พัฒนาหน้าจอ Execution History Table:
  - TanStack Table แสดงประวัติการรันย้อนหลัง
  - Filter ตามสถานะ, วันที่, ชื่องาน, และผู้สั่งรัน
  - Drill-down Modal แสดง Step-by-step Logs และ Metadata
- [x] **TASK-704 [P0]** พัฒนา **AWS SES Notification Engine**:
  - เชื่อมต่อ AWS SDK for Java v2 (`software.amazon.awssdk:ses`)
  - รองรับการตั้งค่า Verified Sender Identity และ Region
  - มี Queue และ Rate Limiter ป้องกันการยิงเกิน SES Sending Quota
- [x] **TASK-705 [P1]** พัฒนา HTML Responsive Email Template (Thymeleaf):
  - Template แจ้งผลสำเร็จ (Pipeline Success Summary: เวลาที่ใช้, ขนาดไฟล์, จำนวน Chunk)
  - Template แจ้งเตือนข้อผิดพลาด (Pipeline Failure Alert: ระบุ Step ที่ Error, Exit code, แนบ 50 บรรทัดสุดท้ายของ Log)
  - Template สำหรับรีเซ็ตรหัสผ่านและสร้างผู้ใช้ใหม่

---

## หมวดที่ 8: การทดสอบ ความมั่นคงปลอดภัย และการนำขึ้นใช้งาน (Testing, Hardening & Deployment)
*Prerequisite: หมวดที่ 1 - 7*

- [x] **TASK-801 [P0]** จัดทำ Automated Integration Test ด้วย **Testcontainers**:
  - ทดสอบการรัน Flow สำรองฐานข้อมูลจริงบน MySQL/PostgreSQL Container
  - ทดสอบการหั่นไฟล์และอัปโหลดเข้า SFTP Test Container พร้อมตรวจสอบ Checksum
- [x] **TASK-802 [P0]** ทำ Security Audit & Hardening:
  - ทดสอบ Injection ผ่านชื่อฐานข้อมูล, คำสั่ง และพารามิเตอร์ของ Task
  - ทดสอบ Account Lockout และ Token Revocation
  - ทดสอบถอดรหัสใน Database โดยไม่มี Master Key (ต้องล้มเหลว)
- [x] **TASK-803 [P1]** ทำ Fault Tolerance & Resilience Test:
  - จำลอง Network Drop ระหว่างส่ง Chunk แล้วทดสอบ Auto-retry
  - ทดสอบ Storage Pre-check กับโฟลเดอร์ที่พื้นที่เหลือน้อย
  - ทดสอบ Watchdog Kill คำสั่งที่จำลองการค้าง (Hung Process)
- [x] **TASK-804 [P1]** จัดทำ Dockerfile แบบ Multi-stage Build สำหรับ Backend และ Frontend
- [x] **TASK-805 [P1]** จัดทำ Production `docker-compose.prod.yml` และเปิดใช้งาน Spring Boot Actuator
- [x] **TASK-806 [P2]** จัดทำเอกสารคู่มือระบบ (API Documentation, Installation Guide, Operator Manual)
