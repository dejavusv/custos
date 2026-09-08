package com.custos;

import com.custos.modules.transfer.client.RemoteTransferClient;
import com.custos.modules.transfer.dto.StoragePrecheckRequest;
import com.custos.modules.transfer.dto.StoragePrecheckResult;
import com.custos.modules.transfer.engine.ChunkSplitterEngine;
import com.custos.modules.transfer.model.ChunkMetadata;
import com.custos.modules.transfer.model.TransferManifest;
import com.custos.modules.transfer.service.ChecksumService;
import com.custos.modules.transfer.service.StoragePrecheckService;
import com.custos.modules.transfer.client.FtpTransferClient;
import com.custos.modules.transfer.dto.TransferRequest;
import com.custos.modules.transfer.service.ResilientTransferService;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.entity.CredentialVault;
import com.custos.modules.vault.repository.CredentialVaultRepository;
import com.custos.modules.vault.service.VaultService;
import com.custos.modules.vault.dto.DecryptedSecretPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TransferTests {

    @Autowired
    private StoragePrecheckService storagePrecheckService;

    @Autowired
    private ChecksumService checksumService;

    @Autowired
    private ChunkSplitterEngine chunkSplitterEngine;

    @Autowired
    private ResilientTransferService resilientTransferService;

    @Autowired
    private CredentialVaultRepository credentialVaultRepository;

    @Autowired
    private VaultService vaultService;

    @Test
    @DisplayName("TASK-501: ทดสอบ Storage Pre-check ตรวจสอบขนาดพื้นที่ว่างและตรวจจับพื้นที่ไม่พอ")
    void testStoragePrecheckValidation(@TempDir Path tempDir) {
        // 1. Reasonable space requirement should pass
        StoragePrecheckRequest normalRequest = StoragePrecheckRequest.builder()
                .path(tempDir.toString())
                .requiredBytes(10 * 1024 * 1024L) // 10 MB
                .safetyMargin(1.2)
                .build();

        StoragePrecheckResult normalResult = storagePrecheckService.checkStorage(normalRequest);
        assertNotNull(normalResult);
        assertTrue(normalResult.isHasEnoughSpace(), "Reasonable requirement should have enough space");
        assertTrue(normalResult.getUsableSpaceBytes() > 0);

        // 2. Impossible requirement (1,000,000 TB) should fail
        StoragePrecheckRequest excessiveRequest = StoragePrecheckRequest.builder()
                .path(tempDir.toString())
                .requiredBytes(Long.MAX_VALUE / 2)
                .safetyMargin(1.0)
                .build();

        StoragePrecheckResult excessiveResult = storagePrecheckService.checkStorage(excessiveRequest);
        assertNotNull(excessiveResult);
        assertFalse(excessiveResult.isHasEnoughSpace(), "Excessive space requirement must be rejected");

        // 3. assertSufficientSpace should throw for excessive
        assertThrows(IllegalStateException.class, () ->
                storagePrecheckService.assertSufficientSpace(tempDir.toString(), Long.MAX_VALUE / 2, 1.0));
    }

    @Test
    @DisplayName("TASK-503: ทดสอบ ChecksumService คำนวณ SHA-256 และตรวจสอบความถูกต้อง")
    void testChecksumServiceCalculation(@TempDir Path tempDir) throws IOException {
        Path sampleFile = tempDir.resolve("sample.txt");
        Files.writeString(sampleFile, "Custos SHA-256 Checksum Engine Verification");

        String sha256 = checksumService.calculateSha256(sampleFile.toFile());
        assertNotNull(sha256);
        assertEquals(64, sha256.length(), "SHA-256 hex string must be exactly 64 characters");

        // Verification matches
        assertTrue(checksumService.verifyChecksum(sampleFile.toFile(), sha256));
        assertTrue(checksumService.verifyChecksum(sampleFile.toFile(), sha256.toUpperCase()));

        // Tampered checksum fails
        assertFalse(checksumService.verifyChecksum(sampleFile.toFile(), "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    @DisplayName("TASK-502 & TASK-503: ทดสอบ Chunk Splitter Engine ตัดแบ่งไฟล์และสร้าง Manifest")
    void testChunkSplitterAndManifestCreation(@TempDir Path tempDir) throws IOException {
        Path sourceFile = tempDir.resolve("large_archive.tar.gz");
        // Create a 200 KB test payload
        byte[] payload = new byte[200 * 1024];
        new Random(42).nextBytes(payload);
        Files.write(sourceFile, payload);

        Path splitOutputDir = tempDir.resolve("chunks_output");
        Files.createDirectories(splitOutputDir);

        // Split in 64 KB chunks -> Should produce 4 chunks (64KB + 64KB + 64KB + 8KB)
        long chunkSize = 64 * 1024L;
        TransferManifest manifest = chunkSplitterEngine.splitFile(sourceFile.toFile(), splitOutputDir.toFile(), chunkSize);

        assertNotNull(manifest);
        assertEquals(sourceFile.getFileName().toString(), manifest.getOriginalFileName());
        assertEquals(payload.length, manifest.getOriginalSizeBytes());
        assertEquals(4, manifest.getTotalChunks());
        assertEquals(4, manifest.getChunks().size());

        // Verify sequential naming and individual chunk checksums
        List<ChunkMetadata> chunks = manifest.getChunks();
        assertEquals("large_archive.tar.gz.part01", chunks.get(0).getFileName());
        assertEquals("large_archive.tar.gz.part02", chunks.get(1).getFileName());
        assertEquals("large_archive.tar.gz.part03", chunks.get(2).getFileName());
        assertEquals("large_archive.tar.gz.part04", chunks.get(3).getFileName());

        for (ChunkMetadata chunk : chunks) {
            File chunkFile = new File(chunk.getFilePath());
            assertTrue(chunkFile.exists());
            assertEquals(chunk.getSizeBytes(), chunkFile.length());
            assertEquals(chunk.getChecksumSha256(), checksumService.calculateSha256(chunkFile));
        }

        // Test Roundtrip Merging: Merge 4 chunks back into single file
        File mergedFile = tempDir.resolve("merged_restored.tar.gz").toFile();
        List<File> chunkFiles = Arrays.asList(
                new File(chunks.get(0).getFilePath()),
                new File(chunks.get(1).getFilePath()),
                new File(chunks.get(2).getFilePath()),
                new File(chunks.get(3).getFilePath())
        );

        File restored = chunkSplitterEngine.mergeChunks(chunkFiles, mergedFile);
        assertNotNull(restored);
        assertTrue(restored.exists());
        assertEquals(sourceFile.toFile().length(), restored.length());

        // SHA-256 of restored merged file must be identical to original file!
        assertEquals(manifest.getOriginalChecksumSha256(), checksumService.calculateSha256(restored));
    }

    @Test
    @DisplayName("TASK-506: จำลอง Resilient Sequential Transfer และ Auto-Retry เฉพาะ Chunk ที่หลุด")
    void testResilientSequentialRetrySimulation() {
        // Mock client that fails on chunk 2 on first attempt, but succeeds on second attempt
        Set<String> uploadedChunks = new HashSet<>();
        Map<String, Integer> attemptCounter = new HashMap<>();

        RemoteTransferClient mockClient = new RemoteTransferClient() {
            @Override
            public void connect() {}

            @Override
            public void disconnect() {}

            @Override
            public boolean isConnected() {
                return true;
            }

            @Override
            public void uploadFile(File localFile, String remoteDirectory, String remoteFileName) {
                int attempts = attemptCounter.getOrDefault(remoteFileName, 0) + 1;
                attemptCounter.put(remoteFileName, attempts);

                // Simulate temporary network drop on chunk 2 first attempt
                if (remoteFileName.endsWith("part02") && attempts == 1) {
                    throw new RuntimeException("Simulated network timeout on chunk 2");
                }
                uploadedChunks.add(remoteFileName);
            }

            @Override
            public boolean remoteFileExists(String remoteDirectory, String remoteFileName) {
                return uploadedChunks.contains(remoteFileName);
            }

            @Override
            public long getRemoteFileSize(String remoteDirectory, String remoteFileName) {
                return uploadedChunks.contains(remoteFileName) ? 100L : -1L;
            }
        };

        // Simulate sequential upload loop for 3 chunks
        List<String> chunkNames = Arrays.asList("backup.tar.gz.part01", "backup.tar.gz.part02", "backup.tar.gz.part03");
        int totalRetries = 0;

        for (String chunkName : chunkNames) {
            boolean uploaded = false;
            int maxRetries = 3;
            int attempt = 0;

            while (!uploaded && attempt <= maxRetries) {
                try {
                    mockClient.uploadFile(new File(chunkName), "/upload", chunkName);
                    uploaded = true;
                } catch (Exception e) {
                    attempt++;
                    totalRetries++;
                    if (attempt > maxRetries) {
                        fail("Retries exceeded for " + chunkName);
                    }
                }
            }
        }

        // Verify:
        // Chunk 1 uploaded in 1 attempt
        assertEquals(1, attemptCounter.get("backup.tar.gz.part01"));
        // Chunk 2 retried once and succeeded in 2 attempts
        assertEquals(2, attemptCounter.get("backup.tar.gz.part02"));
        // Chunk 3 uploaded in 1 attempt (did NOT start over from chunk 1!)
        assertEquals(1, attemptCounter.get("backup.tar.gz.part03"));
        // Total retries == 1
        assertEquals(1, totalRetries);
        // All 3 chunks reached destination
        assertEquals(3, uploadedChunks.size());
    }

    @Test
    @DisplayName("TASK-504: ทดสอบ FtpTransferClient รองรับโหมด Explicit TLS (FTPS)")
    void testFtpTransferClientExplicitTlsFlag() {
        FtpTransferClient plainClient = new FtpTransferClient("ftp.example.com", 21, "user", "pass", false);
        assertFalse(plainClient.isFtps(), "Plain FTP must have isFtps = false");

        FtpTransferClient ftpsClient = new FtpTransferClient("ftps.example.com", 21, "user", "pass", true, true);
        assertTrue(ftpsClient.isFtps(), "FTPS Explicit client must have isFtps = true");
    }

    @Test
    @DisplayName("TASK-504: ทดสอบ ResilientTransferService สามารถ Resolve Credential FTP ที่ตั้งค่า Explicit TLS ได้ถูกต้อง")
    void testResolveFtpTransferClientWithExplicitTlsMetadata() throws Exception {
        String encrypted = vaultService.encryptJson(DecryptedSecretPayload.builder().password("ftpPass123").build());
        CredentialVault vault = CredentialVault.builder()
                .name("test-ftps-explicit-credential-" + UUID.randomUUID())
                .credentialType(CredentialType.FTP)
                .host("ftps.example.com")
                .port(21)
                .username("ftpuser")
                .encryptedData(encrypted)
                .extraMetadata("{\"ftpEncryption\":\"EXPLICIT_TLS\",\"trustSelfSigned\":true}")
                .build();
        vault = credentialVaultRepository.save(vault);

        try {
            Method method = ResilientTransferService.class.getDeclaredMethod("resolveTransferClient", TransferRequest.class);
            method.setAccessible(true);

            TransferRequest req = TransferRequest.builder()
                    .sourceFilePath("dummy.tar.gz")
                    .credentialId(vault.getId())
                    .build();

            RemoteTransferClient resolved = (RemoteTransferClient) method.invoke(resilientTransferService, req);
            assertNotNull(resolved);
            assertTrue(resolved instanceof FtpTransferClient, "Resolved client must be FtpTransferClient");
            assertTrue(((FtpTransferClient) resolved).isFtps(), "Credential with EXPLICIT_TLS must resolve with isFtps = true");
        } finally {
            credentialVaultRepository.delete(vault);
        }
    }

    @Test
    @DisplayName("TASK-504: ทดสอบ ResilientTransferService สามารถ Resolve CredentialType.FTPS ได้เป็น FTPS เสมอ")
    void testResolveFtpsTransferClientWithCredentialTypeFtps() throws Exception {
        String encrypted = vaultService.encryptJson(DecryptedSecretPayload.builder().password("ftpsPass").build());
        CredentialVault vault = CredentialVault.builder()
                .name("test-ftps-type-credential-" + UUID.randomUUID())
                .credentialType(CredentialType.FTPS)
                .host("ftps.example.com")
                .port(21)
                .username("ftpsuser")
                .encryptedData(encrypted)
                .build();
        vault = credentialVaultRepository.save(vault);

        try {
            Method method = ResilientTransferService.class.getDeclaredMethod("resolveTransferClient", TransferRequest.class);
            method.setAccessible(true);

            TransferRequest req = TransferRequest.builder()
                    .sourceFilePath("dummy.tar.gz")
                    .credentialId(vault.getId())
                    .build();

            RemoteTransferClient resolved = (RemoteTransferClient) method.invoke(resilientTransferService, req);
            assertNotNull(resolved);
            assertTrue(resolved instanceof FtpTransferClient, "Resolved client must be FtpTransferClient");
            assertTrue(((FtpTransferClient) resolved).isFtps(), "Credential of type FTPS must resolve with isFtps = true");
        } finally {
            credentialVaultRepository.delete(vault);
        }
    }

    @Test
    @DisplayName("TASK-504: ทดสอบ ResilientTransferService สามารถ Resolve Direct Request ที่ระบุ FTPS หรือ Explicit TLS ได้ถูกต้อง")
    void testResolveFtpTransferClientDirectExplicitTls() throws Exception {
        Method method = ResilientTransferService.class.getDeclaredMethod("resolveTransferClient", TransferRequest.class);
        method.setAccessible(true);

        TransferRequest req1 = TransferRequest.builder()
                .sourceFilePath("dummy.tar.gz")
                .protocol("FTPS")
                .host("ftps.example.com")
                .port(21)
                .username("user")
                .password("pass")
                .build();
        RemoteTransferClient resolved1 = (RemoteTransferClient) method.invoke(resilientTransferService, req1);
        assertTrue(((FtpTransferClient) resolved1).isFtps(), "Protocol FTPS must resolve with isFtps = true");

        TransferRequest req2 = TransferRequest.builder()
                .sourceFilePath("dummy.tar.gz")
                .protocol("FTP")
                .ftpEncryption("EXPLICIT_TLS")
                .host("ftps.example.com")
                .port(21)
                .username("user")
                .password("pass")
                .build();
        RemoteTransferClient resolved2 = (RemoteTransferClient) method.invoke(resilientTransferService, req2);
        assertTrue(((FtpTransferClient) resolved2).isFtps(), "Protocol FTP with EXPLICIT_TLS must resolve with isFtps = true");
    }
}
