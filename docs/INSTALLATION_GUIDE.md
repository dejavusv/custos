# Custos Platform - Installation & Deployment Guide

คู่มือฉบับนี้อธิบายขั้นตอนการติดตั้ง กำหนดค่าความปลอดภัย และการนำ Custos Platform ขึ้นใช้งานบนสภาพแวดล้อม Production ด้วย Docker Compose

---

## สารบัญ
1. [สถาปัตยกรรมระบบ (System Architecture)](#1-สถาปัตยกรรมระบบ)
2. [ความต้องการของระบบ (Prerequisites)](#2-ความต้องการของระบบ)
3. [การสร้างกุญแจความปลอดภัย (Security Keys Generation)](#3-การสร้างกุญแจความปลอดภัย)
4. [การติดตั้งบนสภาพแวดล้อม Development](#4-การติดตั้งบนสภาพแวดล้อม-development)
5. [การนำขึ้นใช้งานบนสภาพแวดล้อม Production](#5-การนำขึ้นใช้งานบนสภาพแวดล้อม-production)
6. [การทำ Database Migration ด้วย Flyway](#6-การทำ-database-migration-ด้วย-flyway)
7. [การตรวจสอบความพร้อมใช้งาน (Healthcheck & Monitoring)](#7-การตรวจสอบความพร้อมใช้งาน)
8. [การสำรองข้อมูลระบบและการกู้คืน (Backup & Disaster Recovery)](#8-การสำรองข้อมูลระบบ)

---

## 1. สถาปัตยกรรมระบบ

Custos ถูกออกแบบให้ทำงานเป็นอิสระ ปลอดภัย และขยายขนาดได้ง่าย:

```
[ Web Browser ]
      │
      ▼ (Port 80 / 443)
┌────────────────────────────────────────────────────────┐
│ custos-frontend (Nginx Alpine Reverse Proxy + React)   │
│  - SPA Static Asset Caching                            │
│  - Reverse Proxy /api/ -> backend:8080                 │
│  - Reverse Proxy /ws   -> backend:8080 (WebSocket)     │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│ custos-backend (Spring Boot 3.3.4, JRE 21 Non-root)    │
│  - Sandboxed Process Engine (mysqldump, pg_dump, tar)  │
│  - Credential Vault (AES-256-GCM)                      │
│  - Quartz Scheduler (Clustered / Persistent)           │
│  - DAG Pipeline Engine & STOMP Broadcaster             │
└──────────────┬─────────────────────────┬───────────────┘
               │                         │
               ▼                         ▼
┌──────────────────────────┐  ┌──────────────────────────┐
│ custos-postgres (v16)    │  │ custos-redis (v7)        │
│  - Schema & Audit Logs   │  │  - Cache & Lock Registry │
│  - Quartz JDBC JobStore  │  └──────────────────────────┘
└──────────────────────────┘
```

---

## 2. ความต้องการของระบบ

### Hardware Recommendations
- **CPU:** 2 vCPU ขึ้นไป (แนะนำ 4 vCPU สำหรับการบีบอัดไฟล์และคำนวณ Checksum ขนาดใหญ่)
- **RAM:** 4 GB ขึ้นไป (แนะนำ 8 GB)
- **Disk Space:** ขั้นต่ำ 20 GB สำหรับระบบ และพื้นที่เพิ่มเติมตามขนาด Backup Staging ที่ต้องการ

### Software Prerequisites
- **Operating System:** Linux (Ubuntu 22.04 LTS+, Debian 12+, RHEL 9+) หรือ Windows Server 2022+ / Windows 11 with WSL2
- **Docker Engine:** เวอร์ชัน 24.0 ขึ้นไป
- **Docker Compose:** เวอร์ชัน v2.20 ขึ้นไป

---

## 3. การสร้างกุญแจความปลอดภัย

ก่อนเริ่มใช้งานระบบ Production **ต้องสร้างกุญแจความลับใหม่ทั้งหมด** ห้ามใช้ค่าเริ่มต้นเด็ดขาด:

### 3.1 สร้าง Vault Master Key (AES-256-GCM 32-Byte HEX)
กุญแจนี้ใช้สำหรับเข้ารหัสข้อมูลลับทั้งหมดใน Vault (รหัสผ่าน DB, SSH Key):
```bash
openssl rand -hex 32
```
*ตัวอย่างผลลัพธ์:* `a4f89d31b2c45e890f12456789abcdef0123456789abcdef0123456789abcdef`

> [!CAUTION]
> **สำคัญมาก:** กุญแจ Master Key นี้ต้องถูกสำรองเก็บไว้ใน Password Manager หรือ Secret Store ภายนอก หากทำกุญแจนี้หาย จะไม่สามารถถอดรหัส Credential ใดๆ ในระบบได้อีกต่อไป

### 3.2 สร้าง JWT Secret Key (512-Bit HEX)
กุญแจนี้ใช้สำหรับการลงลายมือชื่อดิจิทัลของ Access Token และ Refresh Token:
```bash
openssl rand -hex 64
```

---

## 4. การติดตั้งบนสภาพแวดล้อม Development

สำหรับนักพัฒนาที่ต้องการทดสอบระบบบนเครื่อง Local:

1. สตาร์ทฐานข้อมูลและ Mock SFTP Server:
```bash
cd docker
docker compose -f docker-compose.dev.yml up -d
```

2. รัน Backend ในโหมด Local:
```bash
cd ../backend
./mvnw spring-boot:run
```

3. รัน Frontend Vite Dev Server:
```bash
cd ../frontend
npm install
npm run dev
```
เข้าใช้งานผ่านเบราว์เซอร์ที่: `http://localhost:5173`

---

## 5. การนำขึ้นใช้งานบนสภาพแวดล้อม Production

1. **คัดลอกไฟล์ Environment และตั้งค่าตัวแปร:**
```bash
cd docker
cp .env.prod.example .env.prod
nano .env.prod
```

2. **กรอกค่าความลับที่สร้างไว้ใน `.env.prod`:**
```ini
POSTGRES_DB=custos_db
POSTGRES_USER=custos_prod_user
POSTGRES_PASSWORD=UltraSecureDatabasePassword2026!

CUSTOS_JWT_SECRET=<512-bit HEX จากขั้นตอนที่ 3.2>
CUSTOS_VAULT_MASTER_KEY=<32-byte HEX จากขั้นตอนที่ 3.1>

CUSTOS_AWS_SES_ENABLED=true
CUSTOS_AWS_SES_FROM_EMAIL=alerts@yourdomain.com
AWS_REGION=ap-southeast-1
AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE
AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
```

3. **สั่ง Build และรัน Production Stack ทั้งหมด:**
```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

4. **ตรวจสอบสถานะคอนเทนเนอร์:**
```bash
docker compose -f docker-compose.prod.yml ps
```
คอนเทนเนอร์ทุกตัวต้องมีสถานะ `healthy` หรือ `Up`:
- `custos-postgres-prod` (healthy)
- `custos-redis-prod` (healthy)
- `custos-backend-prod` (healthy)
- `custos-frontend-prod` (Up)

---

## 6. การทำ Database Migration ด้วย Flyway

ระบบ Custos ติดตั้ง **Flyway Migration Engine** ไว้ในตัว เมื่อ Backend เริ่มทำงาน Flyway จะทำการอัปเดต Schema ของฐานข้อมูลอัตโนมัติ:
- `V1__init_auth_schema.sql`: ตารางผู้ใช้ บทบาท และ Refresh Token
- `V2__init_vault_schema.sql`: ตาราง Credential Profiles และ AES-GCM Encrypted Vault
- `V3__init_backup_schema.sql`: ตาราง Task Backup และ Retention History
- `V4__init_pipeline_schema.sql`: ตาราง Pipeline DAG Nodes และ Quartz Scheduler Cluster Tables

หากต้องการตรวจสอบประวัติ Migration ใน Database:
```sql
SELECT installed_rank, version, description, success FROM flyway_schema_history;
```

---

## 7. การตรวจสอบความพร้อมใช้งาน

Custos เปิดใช้งาน **Spring Boot Actuator** เพื่อให้ระบบ Load Balancer หรือ Monitoring Tool (Prometheus, Datadog) เข้ามาตรวจสอบได้:

### Health Probe Endpoint
```bash
curl http://localhost:8080/actuator/health
```
*ตัวอย่างคำตอบ:*
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "PostgreSQL" } },
    "diskSpace": { "status": "UP", "details": { "total": 536870912000, "free": 214748364800 } },
    "ping": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 8. การสำรองข้อมูลระบบ

### สำรองฐานข้อมูล Custos:
```bash
docker exec -t custos-postgres-prod pg_dump -U custos_prod_user custos_db > custos_backup_$(date +%Y%m%d).sql
```

### การกู้คืนฐานข้อมูล:
```bash
docker exec -i custos-postgres-prod psql -U custos_prod_user custos_db < custos_backup_20260906.sql
```
