# System Architecture & Technical Specification: Google Drive Upload with Firebase Audit Log

เอกสารสรุปสถาปัตยกรรมระบบ ข้อกำหนดทางเทคนิค (Technical Specification) และชุดเทคโนโลยี (Tech Stack) สำหรับการต่อขยายฟังก์ชัน **Upload File ขึ้น Google Drive พร้อมบันทึกประวัติการอัปโหลดและระบุชื่อระบบต้นทางลงบน Firebase Cloud Firestore** โดยปรับโครงสร้างให้สอดคล้องกับระบบเดิมที่เป็น **React (Frontend)** และ **Java Spring Boot (Backend)**

---

## 1. ภาพรวมของระบบ (System Overview)

ระบบเป็นการพัฒนาฟังก์ชันเพิ่มเติม (Feature Extension) บนเว็บแอปพลิเคชันเดิม:
1. **Frontend (React):** จัดเตรียม Component สำหรับเลือก/ลากวางไฟล์ (File Picker / Drag & Drop) พร้อมระบุ Metadata เช่น ชื่อระบบ (`systemSource`) หรือประเภทเอกสาร ส่งผ่าน Multipart/Form-data
2. **Backend (Java Spring Boot):** 
   * ทำหน้าที่เป็น API Gateway และ Service Core รับไฟล์เข้ามาผ่าน `MultipartFile`
   * สตรีมไฟล์ตรงไปยัง **Google Drive** ด้วย **Google Drive Client Library for Java**
   * บันทึกข้อมูล Audit Log และ Metadata ลง **Firebase Cloud Firestore** ผ่าน **Firebase Admin Java SDK**
3. **Cloud Storage (Google Drive):** จัดเก็บไฟล์จริง (PDF, Images, Docs ฯลฯ)
4. **Cloud Database (Firebase Cloud Firestore):** จัดเก็บบันทึกประวัติ Transaction การอัปโหลด สามารถค้นหาและกรองย้อนหลังได้ตามชื่อระบบ

---

## 2. โครงสร้างเทคโนโลยีที่ปรับปรุง (Updated Tech Stack)

### 2.1 สรุปสถาปัตยกรรมระบบ (Architecture Matrix)

| ส่วนของระบบ | เทคโนโลยี / เครื่องมือ | บทบาทและหน้าที่ |
|---|---|---|
| **Frontend** | **React** (TypeScript / JavaScript) | • File Upload Component (File Input / Drag & Drop UI)<br>• แสดง Upload Progress Bar และข้อความแจ้งเตือนสถานะ<br>• หน้าจอ Audit Log Dashboard เรียกดูประวัติการอัปโหลดย้อนหลัง |
| **Backend** | **Java Spring Boot** (3.x / 2.7+) | • REST API Endpoint (`@RestController`) รับ `MultipartFile`<br>• Service Layer สำหรับเชื่อมต่อ Google Drive API และ Firestore<br>• DTO / Entity Validation และ Error Handling |
| **File Storage** | **Google Drive API (v3)** | จัดเก็บไฟล์จริงบน Cloud ภายใต้โฟลเดอร์ที่กำหนด |
| **Security & Auth** | **GCP Service Account** | ตรวจสอบสิทธิ์ฝั่งระบบ (Machine-to-Machine) เข้าถึง Google Drive และ Firestore |
| **Audit Database** | **Firebase Cloud Firestore** | ฐานข้อมูล NoSQL Document เก็บ Metadata, วันเวลา, และ `systemSource` |

---

### 2.2 Dependencies สำหรับ Java Spring Boot (Maven / Gradle)

เพิ่ม Dependencies ใน `pom.xml` หรือ `build.gradle` เพื่อรองรับ Google Drive API และ Firebase Admin SDK:

#### Maven (`pom.xml`)
```xml
<dependencies>
    <!-- Google Drive API Client Library for Java -->
    <dependency>
        <groupId>com.google.apis</groupId>
        <artifactId>google-api-services-drive</artifactId>
        <version>v3-rev20230822-2.0.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.auth</groupId>
        <artifactId>google-auth-library-oauth2-http</artifactId>
        <version>1.23.0</version>
    </dependency>
    <dependency>
        <groupId>com.google.api-client</groupId>
        <artifactId>google-api-client</artifactId>
        <version>2.4.0</version>
    </dependency>

    <!-- Firebase Admin Java SDK (รองรับ Firestore) -->
    <dependency>
        <groupId>com.google.firebase</groupId>
        <artifactId>firebase-admin</artifactId>
        <version>9.3.0</version>
    </dependency>
</dependencies>
```

---

## 3. การออกแบบการทำงานของระบบ (System Workflow)

### 3.1 ลำดับการทำงาน (Process Flow)

```text
[ React Frontend ]
       │
       │  1. FormData: file, systemSource="INVENTORY_SYS", uploadedBy="user_01"
       ▼
[ Spring Boot Controller (@PostMapping) ]
       │
       │  2. รับ MultipartFile & ตรวจสอบชนิด/ขนาดไฟล์
       ▼
[ Google Drive Service (Spring Boot) ]
       │
       │  3. Stream ไฟล์ไปยัง Google Drive ผ่าน Service Account
       ▼
[ Google Drive Storage ]
       │
       │  4. ตอบกลับ Drive File ID, webViewLink, webContentLink
       ▼
[ Firestore Service (Spring Boot) ]
       │
       │  5. บันทึก Document ลง Collection `file_upload_history`
       ▼
[ Firebase Cloud Firestore ]
       │
       │  6. ยืนยันการบันทึกสำเร็จ (Write Success)
       ▼
[ React Frontend ]
       ◀── 7. รับผลลัพธ์ JSON (Status: 201 Created + File Info)
```

---

## 4. โครงสร้างข้อมูลใน Firestore (Data Schema)

**Collection:** `file_upload_history`

```json
{
  "id": "doc_auto_generated_id",
  "systemSource": "INVENTORY_MANAGEMENT",    // ชื่อระบบที่ทำการอัปโหลดขึ้นไป
  "driveFileId": "1a2B3c4D5e6F7g8H9i0J",     // File ID จาก Google Drive
  "fileName": "receipt_2026_09.pdf",         // ชื่อไฟล์เดิม
  "fileExtension": "pdf",                    // นามสกุลไฟล์
  "fileSize": 1048576,                       // ขนาดไฟล์ (Bytes)
  "mimeType": "application/pdf",             // MIME Type
  "folderId": "1xyz_DriveFolderId",          // Target Folder ID บน Google Drive
  "webViewLink": "https://drive.google.com/file/d/1a2B3c4D5e6F7g8H9i0J/view",
  "webContentLink": "https://drive.google.com/uc?id=1a2B3c4D5e6F7g8H9i0J&export=download",
  "uploadedBy": "somchai.n",                 // ชื่อผู้ใช้งาน / User ID
  "status": "SUCCESS",                       // สถานะ (SUCCESS / FAILED)
  "createdAt": "2026-09-10T08:30:00.000Z",   // Timestamp
  "updatedAt": "2026-09-10T08:30:00.000Z"
}
```

---

## 5. แนวทางการพัฒนาโค้ด (Implementation Guide)

### 5.1 Spring Boot Service Configuration Example
สามารถใช้ Service Account Key JSON เดียวกันในการ Initialize ทั้ง Google Drive และ Firebase ได้:

```java
@Configuration
public class GoogleCloudConfig {

    @Value("${gcp.service-account.path:classpath:credentials/service-account.json}")
    private Resource serviceAccountResource;

    @Bean
    public Drive googleDriveClient() throws IOException, GeneralSecurityException {
        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountResource.getInputStream())
                .createScoped(Collections.singleton(DriveScopes.DRIVE_FILE));

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName("Spring-Boot-Drive-Uploader")
                .build();
    }

    @Bean
    public Firestore firestoreClient() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccountResource.getInputStream()))
                    .build();
            FirebaseApp.initializeApp(options);
        }
        return FirestoreClient.getFirestore();
    }
}
```

### 5.2 React Upload Handler Example
ตัวอย่างการส่งข้อมูลจาก React ไปยัง API ของ Spring Boot:

```typescript
const handleUpload = async (file: File) => {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("systemSource", "CUSTOMER_PORTAL"); // ระบุชื่อระบบ
  formData.append("uploadedBy", currentUser.username);

  const response = await fetch("/api/v1/drive/upload", {
    method: "POST",
    body: formData,
  });

  const data = await response.json();
  console.log("Upload Success:", data);
};
```

---

## 6. ข้อควรพิจารณาและแนวทางปฏิบัติที่ดี (Best Practices)

1. **Streaming Input/Output:**
   * ใน Spring Boot ให้ใช้ `InputStreamContent` สตรีมข้อมูลจาก `MultipartFile.getInputStream()` ส่งตรงไป Google Drive หลีกเลี่ยงการเขียนลง Local Disk ของ Server ชั่วคราว เพื่อความรวดเร็วและประหยัด RAM
2. **Transaction & Rollback:**
   * หากขั้นตอนการบันทึกข้อมูลลง Firestore ล้มเหลว ควรมี Fallback Mechanism เรียกคำสั่ง `drive.files().delete(fileId)` เพื่อไม่ให้เกิดไฟล์ขยะตกค้างบน Google Drive
3. **การจัดสิทธิ์โฟลเดอร์ Google Drive:**
   * นำอีเมล Service Account (เช่น `xxx@project-id.iam.gserviceaccount.com`) ไปกด Share สิทธิ์ **Editor** ให้กับ Folder ปลายทางบน Drive
4. **การจัดการ Credentials:**
   * จัดเก็บ Service Account Key ผ่าน Spring Boot `application.yml` หรือ Environment Variables (ไม่ Commit ไฟล์ `.json` เข้า Git)
