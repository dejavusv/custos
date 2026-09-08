package com.custos;

import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.repository.UserRepository;
import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.backup.database.MySqlBackupEngine;
import com.custos.modules.backup.database.PostgreSqlBackupEngine;
import com.custos.modules.backup.dto.DatabaseBackupRequest;
import com.custos.modules.backup.dto.FileBackupRequest;
import com.custos.modules.backup.dto.*;
import com.custos.modules.backup.filesystem.FileSystemBackupEngine;
import com.custos.modules.backup.model.BackupResult;
import com.custos.modules.backup.model.CompressionFormat;
import com.custos.modules.backup.service.BackupService;
import com.custos.modules.backup.service.RetentionCleanupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class BackupTests {

    @Autowired
    private MySqlBackupEngine mySqlBackupEngine;

    @Autowired
    private PostgreSqlBackupEngine postgreSqlBackupEngine;

    @Autowired
    private FileSystemBackupEngine fileSystemBackupEngine;

    @Autowired
    private RetentionCleanupService retentionCleanupService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("TASK-401: ทดสอบ MySQL Command Generator, Argument Whitelisting และการซ่อน Password")
    void testMySqlCommandBuildingAndSanitization() {
        DatabaseBackupRequest request = DatabaseBackupRequest.builder()
                .host("127.0.0.1")
                .port(3306)
                .username("db_admin")
                .password("MySuperSecret#123")
                .databaseName("custos_production")
                .tables(Arrays.asList("users", "audit_logs"))
                .build();

        List<String> cmd = mySqlBackupEngine.buildCommandList(request);

        assertNotNull(cmd);
        assertEquals("mysqldump", cmd.get(0));
        assertTrue(cmd.contains("--host=127.0.0.1"));
        assertTrue(cmd.contains("--port=3306"));
        assertTrue(cmd.contains("--user=db_admin"));
        assertTrue(cmd.contains("--single-transaction"));
        assertTrue(cmd.contains("--quick"));
        assertTrue(cmd.contains("custos_production"));
        assertTrue(cmd.contains("users"));
        assertTrue(cmd.contains("audit_logs"));

        // Security check: Password must NEVER be passed as an argument!
        for (String arg : cmd) {
            assertFalse(arg.contains("MySuperSecret#123"), "Password must not be in arguments!");
            assertFalse(arg.startsWith("--password"), "Password flag must not be in arguments!");
        }

        // Test SQL Injection / Dangerous command rejection
        DatabaseBackupRequest maliciousRequest = DatabaseBackupRequest.builder()
                .databaseName("test_db; rm -rf /")
                .build();

        assertThrows(SecurityException.class, () -> mySqlBackupEngine.buildCommandList(maliciousRequest));
    }

    @Test
    @DisplayName("TASK-402: ทดสอบ PostgreSQL Command Generator, Argument Whitelisting และการซ่อน Password")
    void testPostgreSqlCommandBuildingAndSanitization() {
        DatabaseBackupRequest request = DatabaseBackupRequest.builder()
                .host("db.internal.custos")
                .port(5432)
                .username("pg_super")
                .password("PgSecretPassword#999")
                .databaseName("custos_primary")
                .tables(Collections.singletonList("pipeline_definitions"))
                .build();

        List<String> cmd = postgreSqlBackupEngine.buildCommandList(request);

        assertNotNull(cmd);
        assertEquals("pg_dump", cmd.get(0));
        assertTrue(cmd.contains("--host=db.internal.custos"));
        assertTrue(cmd.contains("--port=5432"));
        assertTrue(cmd.contains("--username=pg_super"));
        assertTrue(cmd.contains("--format=p"));
        assertTrue(cmd.contains("--dbname=custos_primary"));
        assertTrue(cmd.contains("-t"));
        assertTrue(cmd.contains("pipeline_definitions"));

        // Security check: Password must NEVER be passed as an argument!
        for (String arg : cmd) {
            assertFalse(arg.contains("PgSecretPassword#999"), "Password must not be in arguments!");
            assertFalse(arg.contains("--password"), "Password flag must not be in arguments!");
        }

        // Test Injection rejection
        DatabaseBackupRequest maliciousRequest = DatabaseBackupRequest.builder()
                .databaseName("valid_db")
                .tables(Collections.singletonList("users; DROP TABLE audit_logs;"))
                .build();

        assertThrows(SecurityException.class, () -> postgreSqlBackupEngine.buildCommandList(maliciousRequest));
    }

    @Test
    @DisplayName("TASK-403: ทดสอบ File & Directory Backup Engine พร้อม Glob Exclusion และ Checksum SHA-256")
    void testFileSystemBackupArchivingAndExclusion(@TempDir Path tempDir) throws IOException {
        // Create sample directory structure
        Path sourceDir = tempDir.resolve("project-to-backup");
        Files.createDirectories(sourceDir);

        Files.writeString(sourceDir.resolve("app.py"), "print('Hello Custos')");
        Files.writeString(sourceDir.resolve("debug.log"), "2026-09-06 DEBUG LOG ENTRY");

        Path subDir = sourceDir.resolve("config");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("application.json"), "{\"app\":\"custos\"}");

        Path nodeModules = sourceDir.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Files.writeString(nodeModules.resolve("package.json"), "{\"name\":\"dummy\"}");

        File targetTarGz = tempDir.resolve("backup_output.tar.gz").toFile();

        FileBackupRequest request = FileBackupRequest.builder()
                .sourcePath(sourceDir.toString())
                .compressionFormat(CompressionFormat.TAR_GZ)
                .exclusionPatterns(Arrays.asList("*.log", "node_modules/**"))
                .build();

        BackupResult result = fileSystemBackupEngine.executeBackup(request, targetTarGz);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(targetTarGz.exists());
        assertTrue(targetTarGz.length() > 0);
        assertNotNull(result.getChecksumSha256());
        assertEquals(64, result.getChecksumSha256().length(), "SHA-256 must be 64 hex characters");

        // We created 4 files, but 2 should be excluded (*.log and node_modules/**) -> 2 files archived
        assertEquals(2, result.getItemCount(), "Exactly 2 non-excluded files should be archived");
    }

    @Test
    @DisplayName("TASK-403: ทดสอบ File Backup ในรูปแบบ ZIP")
    void testFileSystemBackupZipFormat(@TempDir Path tempDir) throws IOException {
        Path sourceDir = tempDir.resolve("zip-source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("document.txt"), "Important report contents");

        File targetZip = tempDir.resolve("archive.zip").toFile();

        FileBackupRequest request = FileBackupRequest.builder()
                .sourcePath(sourceDir.toString())
                .compressionFormat(CompressionFormat.ZIP)
                .build();

        BackupResult result = fileSystemBackupEngine.executeBackup(request, targetZip);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertTrue(targetZip.exists());
        assertEquals(1, result.getItemCount());
        assertEquals(64, result.getChecksumSha256().length());
    }

    @Test
    @DisplayName("TASK-404: ทดสอบ Retention Policy Cleanup ลบไฟล์เก่าเกินกำหนดและคงไฟล์ใหม่ไว้")
    void testRetentionCleanupService(@TempDir Path tempDir) throws IOException {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        UserPrincipal principal = UserPrincipal.create(adminUser);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();

        Path backupDir = tempDir.resolve("backups-retention-test");
        Files.createDirectories(backupDir);

        // 1. Create file 40 days old (should be purged)
        Path oldFile1 = backupDir.resolve("backup_2026_07_20.tar.gz");
        Files.writeString(oldFile1, "Old backup data 1");
        Instant fortyDaysAgo = Instant.now().minus(40, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile1, FileTime.from(fortyDaysAgo));

        // 2. Create file 35 days old (should be purged)
        Path oldFile2 = backupDir.resolve("backup_2026_07_25.sql.gz");
        Files.writeString(oldFile2, "Old backup data 2");
        Instant thirtyFiveDaysAgo = Instant.now().minus(35, ChronoUnit.DAYS);
        Files.setLastModifiedTime(oldFile2, FileTime.from(thirtyFiveDaysAgo));

        // 3. Create fresh file 2 days old (must be preserved!)
        Path freshFile = backupDir.resolve("backup_2026_09_04.tar.gz");
        Files.writeString(freshFile, "Fresh backup data");
        Instant twoDaysAgo = Instant.now().minus(2, ChronoUnit.DAYS);
        Files.setLastModifiedTime(freshFile, FileTime.from(twoDaysAgo));

        RetentionCleanupRequest request = RetentionCleanupRequest.builder()
                .backupDirectory(backupDir.toString())
                .retentionDays(30)
                .filePattern("*.gz")
                .build();

        RetentionCleanupResult cleanupResult = retentionCleanupService.executeRetentionCleanup(request, principal, mockRequest);

        assertNotNull(cleanupResult);
        assertTrue(cleanupResult.isSuccess());
        assertEquals(3, cleanupResult.getScannedFilesCount());
        assertEquals(2, cleanupResult.getDeletedFilesCount());
        assertTrue(cleanupResult.getFreedSpaceBytes() > 0);

        // Verify expired files were deleted
        assertFalse(Files.exists(oldFile1), "Old file 1 should be deleted");
        assertFalse(Files.exists(oldFile2), "Old file 2 should be deleted");

        // Verify fresh file remains intact
        assertTrue(Files.exists(freshFile), "Fresh file must be preserved");
    }

    @Test
    @DisplayName("TASK-406: ทดสอบ Storage Browser API, การเรียงลำดับโฟลเดอร์/ไฟล์ และการป้องกัน Path Traversal")
    void testBrowseStorageAndCreateDirectory(@TempDir Path storageRoot) throws IOException {
        // Prepare directories and files
        Path subDir1 = storageRoot.resolve("daily_backups");
        Path subDir2 = storageRoot.resolve("archive_storage");
        Files.createDirectories(subDir1);
        Files.createDirectories(subDir2);

        Path file1 = storageRoot.resolve("mysql_custos_20260908.sql.gz");
        Path file2 = storageRoot.resolve("report_snapshot.tar.gz");
        Files.writeString(file1, "mysql backup content");
        Files.writeString(file2, "archive content");

        // 1. Browse directory
        StorageBrowseResponse response = backupService.browseStorage(storageRoot.toString());
        assertNotNull(response);
        assertNotNull(response.getItems());
        assertEquals(4, response.getItems().size());

        // Directories must come first
        assertTrue(response.getItems().get(0).isDirectory());
        assertTrue(response.getItems().get(1).isDirectory());
        assertFalse(response.getItems().get(2).isDirectory());
        assertFalse(response.getItems().get(3).isDirectory());

        // Check extensions
        assertEquals("sql.gz", response.getItems().get(2).getExtension());
        assertEquals("tar.gz", response.getItems().get(3).getExtension());

        // 2. Create new subfolder
        StorageItemDto createdFolder = backupService.createDirectory(storageRoot.toString(), "monthly_backups");
        assertNotNull(createdFolder);
        assertEquals("monthly_backups", createdFolder.getName());
        assertTrue(createdFolder.isDirectory());
        assertTrue(Files.exists(storageRoot.resolve("monthly_backups")));

        // 3. Prevent duplicate creation
        assertThrows(IllegalArgumentException.class, () ->
                backupService.createDirectory(storageRoot.toString(), "monthly_backups"));

        // 4. Security checks: Directory Traversal rejection
        assertThrows(SecurityException.class, () ->
                backupService.browseStorage("../../../etc"));
        assertThrows(SecurityException.class, () ->
                backupService.browseStorage("..\\..\\windows"));
        assertThrows(SecurityException.class, () ->
                backupService.createDirectory(storageRoot.toString(), "../dangerous"));
        assertThrows(SecurityException.class, () ->
                backupService.createDirectory(storageRoot.toString(), "sub/nested"));
    }
}
