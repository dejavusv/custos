package com.custos;

import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.drive.controller.GoogleDriveController;
import com.custos.modules.drive.dto.DriveUploadResponse;
import com.custos.modules.drive.model.FileUploadAuditRecord;
import com.custos.modules.drive.service.DriveUploadFacadeService;
import com.custos.modules.drive.service.FirestoreAuditService;
import com.custos.modules.drive.service.GoogleDriveService;
import com.custos.shared.ApiResponse;
import com.google.api.services.drive.model.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleDriveUploadTests {

    @Mock
    private GoogleDriveService googleDriveService;

    @Mock
    private FirestoreAuditService firestoreAuditService;

    private DriveUploadFacadeService driveUploadFacadeService;
    private GoogleDriveController googleDriveController;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        driveUploadFacadeService = new DriveUploadFacadeService(googleDriveService, firestoreAuditService);
        googleDriveController = new GoogleDriveController(driveUploadFacadeService, firestoreAuditService);

        testUser = new UserPrincipal(
                java.util.UUID.randomUUID(),
                "somchai.n",
                "somchai@custos.platform",
                "password123",
                com.custos.modules.auth.entity.UserStatus.ACTIVE,
                null,
                Collections.singletonList(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    @DisplayName("GDRIVE-001: อัปโหลดไฟล์สำเร็จและบันทึกประวัติ Transaction ลง Firestore ถูกต้อง")
    void testUploadAndRecordAuditSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice_2026.pdf",
                "application/pdf",
                "sample PDF content".getBytes()
        );

        File mockDriveFile = new File();
        mockDriveFile.setId("drive_file_9999");
        mockDriveFile.setName("invoice_2026.pdf");
        mockDriveFile.setWebViewLink("https://drive.google.com/file/d/drive_file_9999/view");
        mockDriveFile.setWebContentLink("https://drive.google.com/uc?id=drive_file_9999&export=download");
        mockDriveFile.setParents(Collections.singletonList("folder_abc_123"));

        when(googleDriveService.uploadFile(
                any(InputStream.class),
                eq((long) file.getSize()),
                eq("invoice_2026.pdf"),
                eq("application/pdf"),
                eq("folder_abc_123")
        )).thenReturn(mockDriveFile);

        when(firestoreAuditService.recordUploadLog(any(FileUploadAuditRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DriveUploadResponse response = driveUploadFacadeService.uploadAndRecordAudit(
                file,
                "INVENTORY_MANAGEMENT",
                "folder_abc_123",
                testUser
        );

        assertNotNull(response);
        assertEquals("drive_file_9999", response.getDriveFileId());
        assertEquals("invoice_2026.pdf", response.getFileName());
        assertEquals("pdf", response.getFileExtension());
        assertEquals("INVENTORY_MANAGEMENT", response.getSystemSource());
        assertEquals("somchai.n", response.getUploadedBy());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals("https://drive.google.com/file/d/drive_file_9999/view", response.getWebViewLink());

        // Verify Firestore record content
        ArgumentCaptor<FileUploadAuditRecord> recordCaptor = ArgumentCaptor.forClass(FileUploadAuditRecord.class);
        verify(firestoreAuditService, times(1)).recordUploadLog(recordCaptor.capture());

        FileUploadAuditRecord capturedRecord = recordCaptor.getValue();
        assertEquals("drive_file_9999", capturedRecord.getDriveFileId());
        assertEquals("INVENTORY_MANAGEMENT", capturedRecord.getSystemSource());
        assertEquals("invoice_2026.pdf", capturedRecord.getFileName());
        assertEquals("somchai.n", capturedRecord.getUploadedBy());
        assertEquals("SUCCESS", capturedRecord.getStatus());
        assertNotNull(capturedRecord.getCreatedAt());

        // Verify no rollback was called
        verify(googleDriveService, never()).deleteFile(anyString());
    }

    @Test
    @DisplayName("GDRIVE-002: การทำ Rollback ลบไฟล์ออกจาก Google Drive เมื่อการบันทึก Firestore ล้มเหลว")
    void testRollbackWhenFirestoreFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "failed_audit.pdf",
                "application/pdf",
                "corrupted audit simulation".getBytes()
        );

        File mockDriveFile = new File();
        mockDriveFile.setId("orphan_candidate_file_id");
        mockDriveFile.setName("failed_audit.pdf");

        when(googleDriveService.uploadFile(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(mockDriveFile);

        // Simulate Firestore failure
        when(firestoreAuditService.recordUploadLog(any(FileUploadAuditRecord.class)))
                .thenThrow(new RuntimeException("Firestore network timeout"));

        when(googleDriveService.deleteFile("orphan_candidate_file_id")).thenReturn(true);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            driveUploadFacadeService.uploadAndRecordAudit(
                    file,
                    "BILLING_SERVICE",
                    null,
                    testUser
            );
        });

        assertTrue(thrown.getMessage().contains("Firestore audit stage"));

        // CRITICAL: verify deleteFile was executed to prevent orphan file
        verify(googleDriveService, times(1)).deleteFile("orphan_candidate_file_id");
    }

    @Test
    @DisplayName("GDRIVE-003: ตรวจสอบ Validation ข้อมูลนำเข้า (File ว่างเปล่า หรือ systemSource ขาดหาย)")
    void testInputValidations() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> {
            driveUploadFacadeService.uploadAndRecordAudit(emptyFile, "SYSTEM", null, testUser);
        });

        MockMultipartFile validFile = new MockMultipartFile("file", "valid.txt", "text/plain", "abc".getBytes());
        assertThrows(IllegalArgumentException.class, () -> {
            driveUploadFacadeService.uploadAndRecordAudit(validFile, "   ", null, testUser);
        });
    }

    @Test
    @DisplayName("GDRIVE-004: ทดสอบ Controller Endpoint ทั้ง POST /upload และ GET /history")
    void testControllerEndpoints() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "hello".getBytes()
        );

        File mockDriveFile = new File();
        mockDriveFile.setId("drive_123");
        mockDriveFile.setName("test.txt");

        when(googleDriveService.uploadFile(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(mockDriveFile);

        ResponseEntity<ApiResponse<DriveUploadResponse>> uploadResponse = googleDriveController.uploadFile(
                file,
                "TEST_SYS",
                null,
                testUser
        );

        assertEquals(HttpStatus.CREATED, uploadResponse.getStatusCode());
        assertNotNull(uploadResponse.getBody());
        assertTrue(uploadResponse.getBody().isSuccess());
        assertEquals("drive_123", uploadResponse.getBody().getData().getDriveFileId());

        // Test history
        FileUploadAuditRecord sampleLog = FileUploadAuditRecord.builder()
                .id("doc-1")
                .systemSource("TEST_SYS")
                .driveFileId("drive_123")
                .status("SUCCESS")
                .build();
        when(firestoreAuditService.queryAuditLogs("TEST_SYS", null, 50))
                .thenReturn(List.of(sampleLog));

        ResponseEntity<ApiResponse<List<FileUploadAuditRecord>>> historyResponse =
                googleDriveController.getUploadHistory("TEST_SYS", null, 50);

        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        assertNotNull(historyResponse.getBody());
        assertEquals(1, historyResponse.getBody().getData().size());

        // Test health
        ResponseEntity<ApiResponse<Map<String, Object>>> health = googleDriveController.checkHealth();
        assertEquals("UP", health.getBody().getData().get("status"));
    }
}
