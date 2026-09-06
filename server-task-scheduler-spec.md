# Server Automation & Task Scheduling Platform
## System Architecture, Tech Stack & Functional Specifications (Updated)

---

## 1. ภาพรวมระบบ (Executive Summary)

ระบบ **Server Automation & Task Scheduling Platform** เป็นเว็บแอปพลิเคชันระดับ Enterprise สำหรับบริหารจัดการงานเบื้องหลัง (Background Operations), งานสำรองข้อมูล (Backup & Disaster Recovery), การส่งถ่ายข้อมูล (File Transfer/FTP Streaming) และการร้อยเรียงขั้นตอนงาน (Pipeline Orchestration) บนระบบเซิร์ฟเวอร์ โดยควบคุมสั่งการผ่านหน้าจอ Dashboard แบบ Real-time พร้อมระบบความปลอดภัย การยืนยันตัวตน (Authentication & Authorization), การจัดการสิทธิ์ผู้ใช้งาน (User Management) และระบบส่งอีเมลแจ้งเตือนประสิทธิภาพสูงผ่าน **Amazon SES (Simple Email Service)**

---

## 2. โครงสร้างเทคโนโลยี (Tech Stack Architecture)

สถาปัตยกรรมเป็นแบบ **Decoupled Client-Server Architecture** สื่อสารผ่าน RESTful APIs และ WebSocket (STOMP) เพื่อการสตรีมสถานะและ Log แบบสด

### 2.1 Backend: Java Enterprise Stack
* **Language & Runtime:** Java 21 (LTS)
* **Framework:** Spring Boot 3.x
* **Security & Authentication:**
  * **Spring Security 6.x & JJWT:** จัดการ JWT Token (Access Token + Refresh Token), Password Hashing ด้วย **BCrypt**
  * **Role-Based Access Control (RBAC):** แยกระดับสิทธิ์ผู้ใช้ (เช่น `ROLE_SUPER_ADMIN`, `ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`)
* **Email Service Provider:**
  * **AWS SDK for Java v2 (`software.amazon.awssdk:ses`):** สื่อสารผ่าน AWS SES v2 API หรือ SMTP Interface รองรับ High-throughput Delivery, DKIM/SPF และ Bounce/Complaint Tracking
  * **Template Engine:** Thymeleaf สำหรับ Compile HTML Email Template แบบ Dynamic
* **Scheduling & Batch Engine:**
  * **Quartz Scheduler:** จัดการ Trigger เวลาตามปฏิทิน, Cron Expression, Missfire Handling, Pause/Resume งาน (Cluster-ready ด้วย Database Store)
  * **Spring Batch:** จัดการ Job Lifecycle, Chunk-oriented processing, Step Execution, Skip/Retry Policy
* **Process & CLI Execution:** Java `ProcessBuilder` สำหรับ Execute คำสั่งระดับ OS (เช่น `mysqldump`, `pg_dump`, `tar`, `split`) ภายใต้ Thread Pool และ Sandboxed Execution Context
* **Network & File Transfer:**
  * **Apache Commons Net:** จัดการ FTP / FTPS Connection และ Chunk Streaming
  * **JSch (Java Secure Channel):** รองรับ SFTP / SSH Key Authentication
* **Real-time Messaging:** Spring WebSocket (`spring-boot-starter-websocket`) ร่วมกับ STOMP Broker สำหรับสตรีม Execution Log และ Progress Bar สู่ UI
* **Database & Persistence:**
  * **Spring Data JPA & Hibernate:** จัดการ Data Access Layer
  * **Database (Metadata):** PostgreSQL (เก็บ User, Roles, Task Definition, Cron Config, Pipeline Graph, Audit Logs)

### 2.2 Frontend: Modern React Stack
* **Framework & Tooling:** React, Vite, TypeScript
* **UI Components & Styling:** Tailwind CSS, shadcn/ui (Radix UI primitives), Lucide Icons
* **Pipeline Flow Builder:** **React Flow (@xyflow/react)** สำหรับลากวางโหนดกำหนดลำดับการทำงาน (DAG Workflow)
* **State & Form Management:**
  * **React Hook Form + Zod:** จัดการ Validation ฝั่งหน้าบ้าน (Login, User Form, Task Configuration)
  * **TanStack Query (React Query):** แคชและจัดการ Server State
* **Tables & Data Grids:** TanStack Table (รองรับ Filter, Pagination, Sorting ประวัติการรันและรายชื่อผู้ใช้งาน)
* **Real-time Streaming Client:** `@stomp/stompjs` + `sockjs-client` รับ Log และสถานะการถ่ายโอนไฟล์

---

## 3. รายละเอียดฟังก์ชันระบบ (Functional Specifications)

### 3.1 Authentication & User Management (ระบบล็อกอินและจัดการผู้ใช้งาน)
* **Authentication Screen (หน้าจอเข้าสู่ระบบ):**
  * หน้าจอ Login สวยงาม ปลอดภัย รองรับ Username/Email + Password
  * ระบบ Remember Me / Refresh Token Rotation
  * มาตรการป้องกัน Brute Force: บล็อกบัญชีชั่วคราวเมื่อล็อกอินผิดติดต่อกันเกินจำนวนครั้งที่กำหนด (Account Lockout)
* **User & Role Management Screen (หน้าจอจัดการผู้ใช้งาน):**
  * **CRUD Operations:** สร้าง, แก้ไข, ระงับการใช้งาน (Suspend/Active), ลบผู้ใช้ และรีเซ็ตรหัสผ่าน
  * **Role-Based Access Control (RBAC):**
    * `SUPER_ADMIN`: สิทธิ์เต็มทุกส่วน รวมถึงจัดการผู้ใช้งานและ System Credentials
    * `ADMIN`: จัดการ Task, Pipeline, Schedules, และดู Audit Logs
    * `OPERATOR`: สั่ง Trigger Run/Pause Task และดู Execution Log ได้ แต่แก้ไข Config ไม่ได้
    * `VIEWER`: ดู Dashboard และรายงานผลได้อย่างเดียว
  * **Audit Trail:** บันทึกประวัติการ Login, IP Address, การเปลี่ยนแปลง Config และการสั่งรัน Job ของผู้ใช้แต่ละคน

### 3.2 Notification Engine via AWS SES (ระบบแจ้งเตือนทางอีเมล)
* **AWS SES Integration:**
  * ส่งอีเมลผ่าน AWS SDK v2 (API) หรือ SES SMTP Endpoint
  * กำหนดค่า Sender Identity (Verified Email / Domain) และ AWS Region
  * รองรับ Rate Limiting / Queueing ไม่ให้เกิน SES Sending Quota
* **Notification Scenarios:**
  * แจ้งเตือนเมื่อรันจบ Pipeline ทั้งหมด (Success / Failed)
  * แจ้งเตือนข้อผิดพลาดทันทีราย Process ย่อย (Per-Step Alerting)
  * แจ้งเตือนกรณีระบบกู้คืนรหัสผ่าน (Forgot Password) หรือสร้าง User ใหม่
* **Email Template & Content:**
  * รูปแบบ HTML Responsive Email ออกแบบสะอาด อ่านง่าย
  * สรุปเวลาที่ใช้ (Execution Duration), ขนาดไฟล์ที่ Backup ได้, จำนวน Chunk ที่ส่งสำเร็จ
  * กรณีล้มเหลว (Failed): แสดง Error Details, Exit Code และ 50 บรรทัดสุดท้ายของ Log เพื่อให้ Troubleshoot ได้ทันที

### 3.3 Task Scheduling & Cron Engine (ระบบตั้งเวลาทำงาน)
* **Frequency Configuration:**
  * สำเร็จรูป: รายวัน (Daily), รายสัปดาห์ (Weekly), รายเดือน (Monthly)
  * กำหนดเวลาเจาะจง (Hour / Minute / Timezone)
  * Advanced Cron Expression สำหรับตารางงานซับซ้อน
* **Execution Controls:**
  * สั่งรันทันที (Trigger Now / Manual Run)
  * พักการทำงานชั่วคราว (Pause) / เปิดใช้งานต่อ (Resume)
  * ยกเลิกงานระหว่างทำ (Graceful Kill / Abort Process)

### 3.4 Database Backup Function (ระบบสำรองฐานข้อมูล)
* **Supported Databases:** MySQL, MariaDB, PostgreSQL
* **Features:**
  * จัดการ Database Connection Profile แยกตาม Environment
  * เลือกสำรองข้อมูลทั้งฐานข้อมูล หรือระบุเฉพาะ Tables
  * สตรีมผลลัพธ์ผ่าน Native CLI (`mysqldump` / `pg_dump`) ท่อตรงเข้า Gzip/ZSTD ทันทีโดยไม่กินพื้นที่ RAM
  * ระบบล้างไฟล์สำรองเก่าตามรอบเวลา (Retention Policy Cleanup)

### 3.5 File & Directory Backup Function (ระบบสำรองไฟล์และโฟลเดอร์)
* กำหนด Source Directory Path และ Destination Path อิสระ
* บีบอัดไฟล์ในรูปแบบ `.tar.gz`, `.tar.zst` หรือ `.zip`
* Exclusion Pattern ยกเว้นไฟล์/โฟลเดอร์ที่ไม่จำเป็น (เช่น `node_modules`, `*.log`, `temp/*`)

### 3.6 File Storage & Split Transfer Function (ระบบย้ายและส่งไฟล์แบบแบ่งส่วน)
* **Storage Destinations:** ย้ายเข้า Local Directory, Mounted Volume (NFS/SMB) หรือส่งผ่าน Remote FTP/FTPS/SFTP
* **Large File Chunking (Split Engine):**
  * กำหนดขนาดไฟล์สูงสุดต่อชิ้น (เช่น 100MB, 500MB, 1GB)
  * เมื่อไฟล์เกินขนาด ระบบจะสั่งตัดแบ่งส่วนไฟล์อัตโนมัติ (เช่น `backup.tar.gz.part01`, `backup.tar.gz.part02`)
  * ตรวจสอบ Checksum (SHA-256) ทุกชิ้นไฟล์ก่อนและหลังส่ง การันตีความสมบูรณ์
* **Resilient Transfer:**
  * ทยอยส่งทีละชิ้น (Sequential Chunk Transfer) เพื่อรักษาเสถียรภาพ Network
  * รองรับ Auto-Retry เฉพาะ Chunk ที่หลุดระหว่างส่ง โดยไม่ต้องเริ่มใหม่ตั้งแต่ต้น

### 3.7 Task Sequencing & Pipeline Orchestration (ระบบจัดลำดับงานต่อเนื่อง)
* **Visual Workflow Builder:** ลากวางเชื่อมโยงขั้นตอนงาน (DAG Pipeline) ด้วย React Flow
* **Conditional Branching:**
  * **On Success:** ไปทำงานขั้นตอนถัดไป
  * **On Failure:** ข้ามไปขั้นตอนจัดการข้อผิดพลาด (Fallback / Rollback / Send Alert Mail)
* **Context Passing:** ส่งต่อผลลัพธ์ระหว่าง Step (เช่น DB Backup Step ส่ง Output Path ไปให้ Split & FTP Step)

### 3.8 Monitoring, Execution History & Live Console
* **Real-time Live Console:** หน้าต่าง Terminal บนเว็บ แสดง Output Log บรรทัดต่อบรรทัดแบบ Real-time ผ่าน WebSocket
* **Execution History:** ตารางสืบค้นประวัติย้อนหลัง พร้อม Filter ตามวันที่, ชื่องาน, สถานะ, ผู้สั่งรัน
* **Storage Pre-check:** ตรวจสอบ Disk Space ปลายทางก่อนเริ่มงาน ป้องกันปัญหา Disk Full ระหว่าง Process

---

## 4. แผนผังโครงสร้างฐานข้อมูล (Updated Core Database Schema)

```text
+-----------------------+       +-------------------------+
|        users          |       |         roles           |
+-----------------------+       +-------------------------+
| id (PK)               |       | id (PK)                 |
| username              |       | name (ROLE_ADMIN, ...)  |
| email                 |<----->| description             |
| password_hash         | (N:M) +-------------------------+
| status (ACTIVE/LOCK)  |
| last_login_at         |
+-----------------------+
           | (Audit / Created By)
           v
+-----------------------+       +-------------------------+
|   task_definitions    |       |   pipeline_definitions  |
+-----------------------+       +-------------------------+
| id (PK)               |       | id (PK)                 |
| name                  |       | name                    |
| task_type (DB/FILE/..) |       | cron_expression         |
| config_json           |<------| is_active               |
| created_by (FK:users) |       | created_by (FK:users)   |
+-----------------------+       +-------------------------+
           |                                 |
           |                                 |
           v                                 v
+-----------------------+       +-------------------------+
| pipeline_step_nodes   |       |   pipeline_executions   |
+-----------------------+       +-------------------------+
| id (PK)               |       | id (PK)                 |
| pipeline_id (FK)      |       | pipeline_id (FK)        |
| task_id (FK)          |       | status (RUNNING/OK/ERR) |
| step_order            |       | start_time / end_time   |
| on_success_step_id    |       | triggered_by (FK:users) |
| on_failure_step_id    |       +-------------------------+
+-----------------------+                    |
                                             v
                                +-------------------------+
                                |    step_execution_logs  |
                                +-------------------------+
                                | id (PK)                 |
                                | execution_id (FK)       |
                                | task_id (FK)            |
                                | status                  |
                                | logs_text               |
                                | file_size_bytes         |
                                | aws_ses_message_id      |
                                +-------------------------+
```

---

## 5. มาตรการความปลอดภัยและ Best Practices (Security & Reliability)

1. **Authentication & Password Security:**
   * เข้ารหัสผ่านด้วย BCrypt (Cost Factor >= 12)
   * Token-based authentication ผ่าน Stateless JWT พร้อมระบบ Refresh Token Revocation ใน Redis หรือ DB
2. **Sandboxed Command Execution:**
   * ป้องกัน Command Injection ด้วย Strict Argument Whitelisting ไม่อนุญาตให้อิสระรัน Shell String ตรงๆ
   * รัน Worker ภายใต้สิทธิ์ System Service User ที่จำกัด Directory สิทธิ์เข้าถึง
3. **Secure Vault & AWS IAM:**
   * เข้ารหัสรหัสผ่าน DB/FTP Credentials ด้วย **AES-256-GCM**
   * การต่อ AWS SES ใช้ IAM Role (สำหรับรันบน EC2/ECS) หรือเก็บ Access Key ใน Environment Variables/Vault อย่างปลอดภัย
4. **Graceful Timeout & Resource Throttling:**
   * กำหนด Timeout สูงสุดต่อ Job ป้องกัน Zombie Process
   * จัดสรร I/O Throttling และ Queue Management ไม่ให้กระทบต่อภาระงานหลักของ Server
