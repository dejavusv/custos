# กฎบัตรและมาตรฐานการพัฒนา (Project Rules & Architecture Guidelines)

เอกสารฉบับนี้กำหนดโครงสร้างโปรเจกต์, เทคโนโลยีที่เลือกใช้ (Tech Stack), รูปแบบการเขียนโค้ด (Coding Conventions), และมาตรฐานความปลอดภัย (Security Standards) สำหรับระบบ **Server Automation & Task Scheduling Platform (Custos)** 

---

## 1. รายละเอียด Tech Stack มาตรฐาน (Technology Stack)

### 1.1 Backend Stack
* **Language & Runtime:** Java 21 (LTS)
* **Framework:** Spring Boot 3.3.x+
* **Build Tool:** Maven (หรือ Gradle ตามความเหมาะสมของทีม)
* **Security & Auth:**
  * Spring Security 6.x
  * JJWT (0.12.x+) สำหรับจัดการ Access Token และ Refresh Token
  * Password Hashing: BCrypt (Cost Factor >= 12)
* **Scheduling & Batch:**
  * Quartz Scheduler (กำหนด JobStore เป็น PostgreSQL Database สำหรับรองรับ Clustering ในอนาคต)
  * Spring Batch สำหรับ Chunk Processing และ Step Execution
* **Database & Persistence:**
  * Spring Data JPA & Hibernate 6.x
  * Database: PostgreSQL 16+
  * Database Migration: Flyway
* **Process & CLI Execution:**
  * Java `ProcessBuilder` พร้อม Sandboxed Execution Context, Argument Whitelisting และ Watchdog Timeout
* **Network & File Transfer:**
  * Apache Commons Net 3.x (FTP / FTPS)
  * JSch 0.1.55+ หรือ Apache SSHD (SFTP / SSH Key Authentication)
* **Real-time Messaging:**
  * Spring WebSocket (`spring-boot-starter-websocket`) + Simple In-memory STOMP Broker
* **Notification:**
  * AWS SDK for Java v2 (`software.amazon.awssdk:ses`)
  * Template Engine: Thymeleaf สำหรับ HTML Responsive Email
* **Encryption & Secrets:**
  * AES-256-GCM สำหรับ Credential Vault (DB Connection Strings, FTP/SFTP Passwords, Private Keys)

### 1.2 Frontend Stack
* **Framework & Build:** React 18+, Vite 5+, TypeScript 5+
* **Styling & Components:**
  * Tailwind CSS 3.x
  * shadcn/ui (Radix UI Primitives)
  * Lucide React Icons
* **Workflow / DAG Builder:**
  * React Flow (`@xyflow/react` v12+)
* **State & Data Fetching:**
  * TanStack Query (React Query) v5+
  * Zustand (สำหรับ Global Client State เช่น Auth Token, Active User, Live Session)
* **Form & Validation:**
  * React Hook Form + Zod
* **Data Grids:**
  * TanStack Table v8+
* **Real-time Client:**
  * `@stomp/stompjs` + `sockjs-client`

### 1.3 DevOps & Containerization
* **Container:** Docker & Docker Compose
* **Base Images:** Eclipse Temurin (JDK 21) สำหรับ Backend, Node 20 + Nginx Alpine สำหรับ Frontend
* **Tools inside Backend Container:** `mysqldump`, `pg_dump`, `tar`, `gzip`, `zstd`, `zip`

---

## 2. โครงสร้างโปรเจกต์ (Project Directory Layout)

ระบบจัดโครงสร้างแบบ Decoupled แยกโฟลเดอร์ชัดเจนภายใต้ Root Repository:

```
custos/
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/custos/
│   │   │   │   ├── config/              # Security, WebSocket, Quartz, AWS, Async configs
│   │   │   │   ├── modules/
│   │   │   │   │   ├── auth/            # Auth Controller, Services, JWT filters, User/Role entities
│   │   │   │   │   ├── vault/           # AES-256-GCM Encryption Service, Credential Entities
│   │   │   │   │   ├── execution/       # ProcessBuilder Runner, Argument Whitelist, Log Watcher
│   │   │   │   │   ├── backup/
│   │   │   │   │   │   ├── database/    # MySQL, Postgres Native Dump Streaming
│   │   │   │   │   │   └── filesystem/  # Tar, Zstd, Zip Compression & Exclusion filters
│   │   │   │   │   ├── transfer/        # Split Engine, SHA-256 Checksum, FTP/SFTP Handlers
│   │   │   │   │   ├── scheduling/      # Quartz Job Listeners, Trigger Managers
│   │   │   │   │   ├── pipeline/        # DAG Orchestrator, Context Passing, Branching Engine
│   │   │   │   │   └── notification/    # AWS SES Client, Email Queue, Thymeleaf Mail Service
│   │   │   │   ├── shared/              # BaseEntity, GlobalExceptionHandler, ApiResponse<T>
│   │   │   │   └── CustosApplication.java
│   │   │   └── resources/
│   │   │       ├── db/migration/        # V1__...sql, V2__...sql (Flyway)
│   │   │       ├── templates/email/     # *.html (Thymeleaf templates)
│   │   │       └── application.yml
│   │   └── test/                        # Unit tests & Testcontainers integration tests
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/
│   ├── src/
│   │   ├── components/
│   │   │   ├── ui/                      # shadcn/ui components
│   │   │   ├── layout/                  # Sidebar, Navbar, PageContainer
│   │   │   ├── terminal/                # Live Console Terminal Component
│   │   │   └── flow/                    # React Flow Custom Nodes & Edges
│   │   ├── features/
│   │   │   ├── auth/                    # Login, Reset Password views & hooks
│   │   │   ├── users/                   # User Management, Roles & Permissions
│   │   │   ├── tasks/                   # Task Definition Builders (DB, File, Transfer)
│   │   │   ├── pipelines/               # Pipeline Canvas, Execution History
│   │   │   └── dashboard/               # System Overview, Metrics, Status
│   │   ├── hooks/                       # useWebSocket, useAuth, useDebounce
│   │   ├── services/                    # Axios API Client & Endpoints
│   │   ├── stores/                      # Zustand Stores
│   │   ├── types/                       # TypeScript interfaces, Zod validation schemas
│   │   └── App.tsx
│   ├── package.json
│   ├── vite.config.ts
│   └── tailwind.config.js
│
├── docker/                              # Development & Testing Containers
│   ├── docker-compose.dev.yml
│   └── test-services/
├── docs/                                # เอกสารระบบและคู่มือ
├── plan.md                              # แผนงานภาพรวม
├── rules.md                             # กฎและมาตรฐานโปรเจกต์ (เอกสารนี้)
└── task.md                              # รายการงานและลำดับขั้นตอนการพัฒนา
```

---

## 3. กฎและข้อกำหนดด้านความปลอดภัย (Security Standards)

### 3.1 การป้องกัน Command Injection (เข้มงวดสูงสุด)
* **ห้ามรัน Shell String ตรงๆ เด็ดขาด:** ห้ามใช้ `Runtime.getRuntime().exec("sh -c " + cmd)` หรือต่อ String คำสั่งโดยตรง
* **ใช้ Argument Array Whitelisting เสมอ:** ส่ง Arguments ในรูปของ `List<String>` เข้า `ProcessBuilder`
* **Sanitize Inputs:** พารามิเตอร์ที่เป็นชื่อ Database, Tables, หรือ File Path ต้องผ่านการตรวจสอบ Regex ให้ตรงตามรูปแบบที่ปลอดภัย (เช่น `^[a-zA-Z0-9_\-\./]+$`) ห้ามมีตัวอักษรควบคุม เช่น `;`, `&`, `|`, `` ` ``, `$`, `>`, `<`

### 3.2 การรักษาความลับ Credential (Secret Management)
* ข้อมูลรหัสผ่านฐานข้อมูล, Private Key สำหรับ SSH/SFTP, และรหัสผ่าน FTP **ต้องถูกเข้ารหัสด้วย AES-256-GCM** ก่อนบันทึกลงฟิลด์ในฐานข้อมูล
* **Master Vault Key** ต้องรับมาจาก Environment Variable (`CUSTOS_VAULT_MASTER_KEY`) เท่านั้น ห้าม Hardcode ใน Source Code หรือ `application.yml`
* ห้ามพิมพ์ Plaintext Credentials ลงใน Application Log หรือ WebSocket STOMP Channel

### 3.3 การยืนยันตัวตนและการเข้าถึง (Authentication & RBAC)
* รหัสผ่านผู้ใช้ต้องแฮชด้วย **BCrypt (Strength >= 12)**
* ระบบ JWT:
  * Access Token: หมดอายุในระยะเวลาสั้น (เช่น 15-30 นาที)
  * Refresh Token: เก็บ State ในฐานข้อมูล/Redis เพื่อให้สามารถ Revoke ได้ทันที
* **Account Lockout Policy:** หากล็อกอินผิดพลาดติดต่อกัน 5 ครั้ง ให้ล็อกบัญชีชั่วคราวเป็นเวลา 15 นาที หรือจนกว่า Admin จะปลดล็อก
* ทุก Endpoint ต้องตรวจสอบสิทธิ์ตามตาราง RBAC:
  * `ROLE_SUPER_ADMIN`: สิทธิ์ทุกประการ รวมถึงจัดการ User และ Vault
  * `ROLE_ADMIN`: จัดการ Task, Pipeline, Schedules, ดู Audit Logs
  * `ROLE_OPERATOR`: รัน/หยุด Task และดู Log ได้ แต่แก้ไข Config ไม่ได้
  * `ROLE_VIEWER`: ดูผลลัพธ์และ Dashboard ได้อย่างเดียว

---

## 4. มาตรฐานการพัฒนา Backend (Backend Engineering Rules)

1. **สตรีมข้อมูลแทนการโหลดเข้า RAM (Zero-RAM Streaming):**
   * งาน Database Dump (`mysqldump`, `pg_dump`) ต้องใช้ `InputStream` จาก `ProcessBuilder.getInputStream()` สตรีมตรงเข้า `GZIPOutputStream` / `ZstdOutputStream` แล้วส่งออกไฟล์ปลายทางทันที ห้ามอ่าน Output ทั้งหมดเข้า `byte[]` หรือ Memory Buffer
2. **การตัดแบ่งไฟล์ (Chunking Rules):**
   * ไฟล์ที่มีขนาดเกินขนาดสูงสุดที่ตั้งไว้ ต้องถูกแบ่งเป็น Chunk ตามลำดับ (เช่น `.part01`, `.part02`)
   * ทุก Chunk ต้องคำนวณ Checksum **SHA-256** และบันทึกเปรียบเทียบกับไฟล์ปลายทางก่อนถือว่าสำเร็จ
   * การส่งไฟล์ต้องส่งทีละ Chunk และมี Auto-retry เฉพาะ Chunk ที่ผิดพลาด
3. **การจัดการ Process Timeout & Cleanup:**
   * ทุก Process ที่เรียกผ่าน `ProcessBuilder` ต้องมี Watchdog กำหนด Timeout สูงสุด
   * เมื่อเกิด Exception หรือยกเลิกงาน ต้องสั่ง `process.destroyForcibly()` และลบไฟล์ชั่วคราว (Temp files) ทิ้งเสมอ
4. **Database & Migration Rules:**
   * ห้ามใช้ `ddl-auto: update` ในโหมด Production ให้ใช้ **Flyway** เท่านั้น
   * ชื่อ Migration Script ต้องขึ้นต้นด้วย `V{Version}__{Description}.sql` (ตัวอย่าง: `V1__init_auth_schema.sql`)
   * ฟิลด์เวลาทุกตารางต้องบันทึกเป็น **UTC (Instant / TIMESTAMP WITH TIME ZONE)**
5. **API Response Structure:**
   * Response ทุกตัวต้องอยู่ในมาตรฐานเดียวกัน:
     ```json
     {
       "success": true,
       "data": { ... },
       "message": "Operation completed successfully",
       "timestamp": "2026-09-06T09:30:00Z"
     }
     ```

---

## 5. มาตรฐานการพัฒนา Frontend (Frontend Engineering Rules)

1. **TypeScript Strict Mode:**
   * ห้ามใช้ `any` ยกเว้นกรณีจำเป็นอย่างยิ่ง และต้องมี Comment กำกับเหตุผล
   * กำหนด Interface และ DTO ให้ตรงกับ Backend เสมอ
2. **Form Validation ด้วย Zod:**
   * ทุก Form (Login, Task Definition, User Form) ต้องมี Zod Schema กำกับและ Validate ก่อนส่ง Request ไปยัง Backend
3. **React Flow DAG Architecture:**
   * ห้ามให้ Workflow เกิด Circular Dependency (ต้องมี Validation Cycle Detection ก่อนบันทึก)
   * แยก Custom Node ตามประเภทงาน (DatabaseNode, FileNode, TransferNode, AlertNode)
4. **State Management & Caching:**
   * ใช้ **TanStack Query** จัดการ Server State (Data Fetching, Caching, Polling, Invalidation)
   * ใช้ **Zustand** สำหรับ Client-only State (Theme, Sidebar State, Live Session ID)
5. **WebSocket Connection Handling:**
   * จัดการ Life-cycle การเชื่อมต่อของ STOMP Client ให้ดี (Auto-reconnect เมื่อเน็ตหลุด, Disconnect เมื่อ Unmount Component)

---

## 6. แนวทางข้อตกลง Git & Code Review (Git Conventions)

* **Branch Naming:**
  * `feat/<module-name>-<feature-description>`
  * `fix/<module-name>-<bug-description>`
  * `refactor/<module-name>-<summary>`
* **Commit Message Format (Conventional Commits):**
  * `feat: add AES-256-GCM encryption service for credentials vault`
  * `fix: prevent process hang on large mysqldump stdout buffer`
  * `test: add testcontainers integration test for SFTP transfer`
