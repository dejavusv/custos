# Custos Platform - API Documentation

Custos Platform ให้บริการ REST API และ Real-time WebSocket STOMP สำหรับการจัดการผู้ใช้งาน, คลังจัดเก็บข้อมูลลับ (Vault), ไปป์ไลน์การทำงานแบบ DAG (Pipelines), การสำรองข้อมูล (Backup), การแบ่งส่วนไฟล์และส่งต่อ (Split Transfer), บันทึกการตรวจสอบ (Audit Logs), และการติดตามผลแบบสด (Live Console)

---

## สารบัญ
1. [ภาพรวมการเชื่อมต่อและยืนยันตัวตน (Authentication)](#1-authentication--security)
2. [มาตรฐานรูปแบบผลลัพธ์ (Response Standards)](#2-response-standards)
3. [ระบบยืนยันตัวตนและจัดการผู้ใช้งาน (Auth & Users)](#3-auth--user-management-api)
4. [คลังจัดเก็บรหัสผ่านและคีย์ลับ (Credential Vault)](#4-credential-vault-api)
5. [การจัดการและการร้อยเรียงขั้นตอนงาน (Pipelines & DAG)](#5-pipelines--dag-orchestration-api)
6. [การสตรีมมิ่งสดผ่าน WebSocket STOMP (Live Console Stream)](#6-live-console-websocket-stream)
7. [เครื่องมือสำรองข้อมูลและจัดการไฟล์ (Backup & Transfer API)](#7-backup--transfer-api)
8. [บันทึกกิจกรรมความปลอดภัย (Audit Logs API)](#8-audit-logs-api)
9. [การตรวจสอบสถานะระบบ (Actuator Health & Metrics)](#9-actuator-health--metrics)

---

## 1. Authentication & Security

### JSON Web Token (JWT)
ทุกคำขอ API ยกเว้น `/api/v1/auth/login` และ `/api/v1/auth/refresh-token` จะต้องแนบ Access Token ใน Header:
```http
Authorization: Bearer <ACCESS_TOKEN>
```
- **Access Token:** มีอายุ 15 นาที (900,000 ms)
- **Refresh Token:** มีอายุ 7 วัน (604,800,000 ms) รองรับ Token Rotation อัตโนมัติเมื่อเรียก Refresh
- **บทบาทและสิทธิ์ (Roles):**
  - `ROLE_SUPER_ADMIN`: สิทธิ์สูงสุด สามารถจัดการผู้ใช้, กำหนดบทบาท, ดูบันทึก Audit Logs และจัดการระบบทั้งหมด
  - `ROLE_ADMIN`: สิทธิ์บริหารจัดการระบบ, สร้าง/แก้ไข Credential Vault, สร้างและจัดการ Pipeline
  - `ROLE_OPERATOR`: สิทธิ์สั่งรันงาน (Trigger Now), Pause/Resume, สั่งยกเลิกงาน (Abort), และดู Live Console
  - `ROLE_VIEWER`: สิทธิ์เปิดอ่านข้อมูล (Read-only) สำหรับตรวจสอบสถานะและดูรายงาน

---

## 2. Response Standards

### Successful Response Format
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... }
}
```

### Error Response Format
```json
{
  "success": false,
  "error": "BAD_REQUEST",
  "message": "Validation failed on fields",
  "details": [
    "cronExpression: Invalid cron syntax",
    "nodes: Pipeline must contain at least 1 step node"
  ],
  "timestamp": "2026-09-06T12:00:00Z"
}
```

---

## 3. Auth & User Management API

### 3.1 เข้าสู่ระบบ (Login)
- **Endpoint:** `POST /api/v1/auth/login`
- **Request Body:**
```json
{
  "username": "admin",
  "password": "AdminPassword@123"
}
```
- **Response `200 OK`:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "48b6fa20-410a-45c1-901d-5fe4...",
    "userId": "d290f1ee-6c54-4b01-90e6-d701748f0851",
    "username": "admin",
    "email": "admin@custos.io",
    "roles": ["ROLE_SUPER_ADMIN"]
  }
}
```

### 3.2 ต่ออายุ Token (Refresh Token)
- **Endpoint:** `POST /api/v1/auth/refresh-token`
- **Request Body:**
```json
{
  "refreshToken": "48b6fa20-410a-45c1-901d-5fe4..."
}
```

### 3.3 ออกจากระบบ (Logout & Token Revocation)
- **Endpoint:** `POST /api/v1/auth/logout`
- **Headers:** `Authorization: Bearer <TOKEN>`
- **Response `200 OK`:** ทำการ Revoke Refresh Token ทั้งหมดของผู้ใช้ในระบบ

---

## 4. Credential Vault API

ทุกข้อมูลรหัสผ่านและ SSH Private Key จะถูกเข้ารหัสด้วย **AES-256-GCM** ร่วมกับ Unique 12-byte IV และ 128-bit Authentication Tag ก่อนจัดเก็บลงฐานข้อมูล

### 4.1 รายการ Credential Profiles (Masked Secrets)
- **Endpoint:** `GET /api/v1/vault/credentials`
- **Permissions:** `SUPER_ADMIN`, `ADMIN`
- **Response `200 OK`:**
```json
[
  {
    "id": "290bea36-891f-4fe1-bb37-d0daf793e66f",
    "profileName": "Production-MySQL-Master",
    "credentialType": "DATABASE_MYSQL",
    "host": "db.internal.corp",
    "port": 3306,
    "username": "backup_user",
    "hasPassword": true,
    "hasPrivateKey": false,
    "createdAt": "2026-09-06T10:00:00Z"
  }
]
```

### 4.2 สร้าง Credential Profile
- **Endpoint:** `POST /api/v1/vault/credentials`
- **Request Body:**
```json
{
  "profileName": "Backup-SFTP-Destination",
  "credentialType": "STORAGE_SFTP",
  "host": "sftp.offsite-backup.com",
  "port": 22,
  "username": "custos_uploader",
  "secretData": {
    "password": "UltraSecretPassword123!",
    "privateKey": "-----BEGIN OPENSSH PRIVATE KEY-----\n..."
  }
}
```

---

## 5. Pipelines & DAG Orchestration API

### 5.1 รายการ Pipeline ทั้งหมด
- **Endpoint:** `GET /api/v1/pipelines`
- **Permissions:** `SUPER_ADMIN`, `ADMIN`, `OPERATOR`, `VIEWER`

### 5.2 บันทึก Pipeline Workflow (สร้างหรืออัปเดต DAG Graph)
- **Endpoint:** `POST /api/v1/pipelines` (หรือ `PUT /api/v1/pipelines/{id}`)
- **Request Body:**
```json
{
  "name": "Daily Production DB & Offsite Sync",
  "description": "MySQL Dump + Zstandard Compression + SFTP 500MB Split Upload",
  "cronExpression": "0 0 2 * * ?",
  "timezone": "Asia/Bangkok",
  "misfirePolicy": "SMART_POLICY",
  "isActive": true,
  "nodes": [
    {
      "nodeKey": "dbDump",
      "nodeLabel": "MySQL Master Dump",
      "nodeType": "DATABASE_BACKUP",
      "stepOrder": 1,
      "positionX": 100,
      "positionY": 150,
      "configOverrideJson": "{\"credentialId\":\"...\",\"databaseName\":\"prod_ecommerce\"}",
      "onSuccessNodeId": "550e8400-e29b-41d4-a716-446655440000",
      "onFailureNodeId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
    }
  ]
}
```

### 5.3 สั่งรันงานทันที (Trigger Now)
- **Endpoint:** `POST /api/v1/pipelines/{id}/trigger`
- **Permissions:** `SUPER_ADMIN`, `ADMIN`, `OPERATOR`
- **Response `200 OK`:** คืนค่า `PipelineExecutionResponse` สถานะ `RUNNING` หรือ `PENDING`

### 5.4 สั่งหยุดฉุกเฉิน (Abort Execution)
- **Endpoint:** `POST /api/v1/pipelines/executions/{executionId}/abort`
- **Permissions:** `SUPER_ADMIN`, `ADMIN`, `OPERATOR`

### 5.5 เรียกดูประวัติการรันทั้งหมด (Execution History)
- **Endpoint:** `GET /api/v1/pipelines/executions/all?status=SUCCESS`
- **Query Parameters:** `status` (`SUCCESS`, `FAILED`, `RUNNING`, `ABORTED`)

---

## 6. Live Console WebSocket Stream

Custos ให้บริการสตรีมมิ่ง Log และ Progress แบบ Real-time ผ่าน **WebSocket STOMP**

- **WebSocket Endpoint:** `/ws` (รองรับ SockJS Fallback)
- **Connection Protocol:** STOMP 1.1 / 1.2
- **Topic Channels:**
  - `/topic/pipeline/{executionId}/logs`: สตรีมข้อความ Log แต่ละบรรทัดจากกระบวนการทำงาน
  - `/topic/pipeline/{executionId}/progress`: สตรีมสถานะและ % ความคืบหน้าของการตัดแบ่งและโอนย้ายไฟล์

### Log Message Payload Schema (`/logs`):
```json
{
  "executionId": "b1f868ad-5df9-4fe9-bbf2-fa3f6834d930",
  "stepName": "MySQL Master Dump",
  "level": "INFO",
  "message": "[mysqldump] Table users exported successfully (14,230 rows)",
  "timestamp": "2026-09-06T13:45:00.120Z"
}
```

### Progress Update Payload Schema (`/progress`):
```json
{
  "executionId": "b1f868ad-5df9-4fe9-bbf2-fa3f6834d930",
  "stepNodeId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "stepName": "SFTP Chunk Uploader",
  "percentage": 75,
  "currentChunk": 3,
  "totalChunks": 4,
  "bytesTransferred": 157286400,
  "totalBytes": 209715200,
  "status": "UPLOADING",
  "timestamp": "2026-09-06T13:45:10.500Z"
}
```

---

## 7. Backup & Transfer API

### 7.1 ตรวจสอบพื้นที่ว่างปลายทาง (Storage Pre-check)
- **Endpoint:** `POST /api/v1/transfer/precheck`
- **Request Body:**
```json
{
  "targetPath": "/var/backups",
  "requiredBytes": 10737418240,
  "safetyMargin": 1.2
}
```
- **Response `200 OK`:**
```json
{
  "targetPath": "/var/backups",
  "usableSpaceBytes": 214748364800,
  "totalSpaceBytes": 536870912000,
  "requiredBytes": 10737418240,
  "safetyMargin": 1.2,
  "hasEnoughSpace": true,
  "freeSpacePercentage": 40.0,
  "message": "Storage check passed: 204800 MB usable space available"
}
```

---

## 8. Audit Logs API

- **Endpoint:** `GET /api/v1/audit-logs`
- **Permissions:** `SUPER_ADMIN`, `ADMIN`
- **Query Parameters:** `page`, `size`, `username`, `action`
- **Response `200 OK`:** คืนค่า Pageable รายการการกระทำ เช่น `LOGIN_SUCCESS`, `LOGIN_FAILED`, `CREDENTIAL_CREATED`, `PIPELINE_TRIGGERED`

---

## 9. Actuator Health & Metrics

- **Endpoint:** `GET /actuator/health`
  - คืนสถานะ `{"status":"UP"}` เมื่อ Database, Redis, Disk Space และ Quartz Scheduler พร้อมทำงาน
- **Endpoint:** `GET /actuator/info`
- **Endpoint:** `GET /actuator/metrics`
