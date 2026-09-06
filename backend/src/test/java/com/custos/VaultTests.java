package com.custos;

import com.custos.modules.auth.entity.User;
import com.custos.modules.auth.repository.UserRepository;
import com.custos.modules.auth.security.UserPrincipal;
import com.custos.modules.execution.ProcessExecutionResult;
import com.custos.modules.execution.ProcessExecutorService;
import com.custos.modules.execution.ProcessSanitizer;
import com.custos.modules.execution.RingBuffer;
import com.custos.modules.vault.dto.CreateCredentialRequest;
import com.custos.modules.vault.dto.CredentialResponse;
import com.custos.modules.vault.dto.DecryptedSecretPayload;
import com.custos.modules.vault.dto.UpdateCredentialRequest;
import com.custos.modules.vault.entity.CredentialType;
import com.custos.modules.vault.service.CredentialService;
import com.custos.modules.vault.service.VaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class VaultTests {

    @Autowired
    private VaultService vaultService;

    @Autowired
    private ProcessSanitizer processSanitizer;

    @Autowired
    private ProcessExecutorService processExecutorService;

    @Autowired
    private CredentialService credentialService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("TASK-302: ทดสอบการเข้ารหัสและถอดรหัสด้วย AES-256-GCM")
    void testVaultEncryptionAndDecryption() {
        String plainText = "SuperSecretDatabasePassword#2026";
        String cipherText1 = vaultService.encrypt(plainText);
        String cipherText2 = vaultService.encrypt(plainText);

        assertNotNull(cipherText1);
        assertNotNull(cipherText2);
        assertNotEquals(plainText, cipherText1);
        // Unique random IV means two encryptions of the same plaintext produce different ciphertexts
        assertNotEquals(cipherText1, cipherText2);

        String decrypted1 = vaultService.decrypt(cipherText1);
        String decrypted2 = vaultService.decrypt(cipherText2);

        assertEquals(plainText, decrypted1);
        assertEquals(plainText, decrypted2);
    }

    @Test
    @DisplayName("TASK-302: ทดสอบการตรวจจับข้อมูลที่ถูกดัดแปลง (Tamper Detection) ใน AES-GCM")
    void testVaultTamperDetection() {
        String plainText = "SecretDataPayload";
        String cipherText = vaultService.encrypt(plainText);

        byte[] rawBytes = Base64.getDecoder().decode(cipherText);
        // Tamper with the last byte (authentication tag)
        rawBytes[rawBytes.length - 1] ^= (byte) 0xFF;
        String tamperedCipher = Base64.getEncoder().encodeToString(rawBytes);

        assertThrows(SecurityException.class, () -> vaultService.decrypt(tamperedCipher));
    }

    @Test
    @DisplayName("TASK-302: ทดสอบการเข้ารหัสและถอดรหัส JSON Object")
    void testVaultJsonEncryption() {
        DecryptedSecretPayload payload = DecryptedSecretPayload.builder()
                .password("dbPass123!")
                .sshPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----")
                .sshPassphrase("keyPassphrase999")
                .build();

        String encrypted = vaultService.encryptJson(payload);
        assertNotNull(encrypted);

        DecryptedSecretPayload decrypted = vaultService.decryptJson(encrypted, DecryptedSecretPayload.class);
        assertNotNull(decrypted);
        assertEquals(payload.getPassword(), decrypted.getPassword());
        assertEquals(payload.getSshPrivateKey(), decrypted.getSshPrivateKey());
        assertEquals(payload.getSshPassphrase(), decrypted.getSshPassphrase());
    }

    @Test
    @DisplayName("TASK-303: ทดสอบ ProcessSanitizer ตรวจจับและปฏิเสธ Command Injection")
    void testProcessSanitizerBlocksCommandInjection() {
        // Safe commands and identifiers
        assertDoesNotThrow(() -> processSanitizer.validateExecutable("mysqldump"));
        assertDoesNotThrow(() -> processSanitizer.validateExecutable("pg_dump"));
        assertDoesNotThrow(() -> processSanitizer.validateExecutable("tar"));
        assertDoesNotThrow(() -> processSanitizer.validateIdentifier("customer_db_2026", "databaseName"));
        assertDoesNotThrow(() -> processSanitizer.validateHostname("192.168.1.100"));
        assertDoesNotThrow(() -> processSanitizer.validateArgument("--all-databases"));

        // Unsafe executables outside whitelist
        assertThrows(SecurityException.class, () -> processSanitizer.validateExecutable("sh"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateExecutable("bash"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateExecutable("rm"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateExecutable("cmd.exe"));

        // Command injection metacharacters
        assertThrows(SecurityException.class, () -> processSanitizer.validateArgument("test; rm -rf /"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateArgument("db & cat /etc/passwd"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateArgument("dump | bash"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateArgument("$(whoami)"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateArgument("`id`"));
        assertThrows(SecurityException.class, () -> processSanitizer.validateIdentifier("db; DROP TABLE users;", "databaseName"));
        assertThrows(SecurityException.class, () -> processSanitizer.validatePath("../../etc/shadow"));
    }

    @Test
    @DisplayName("TASK-303: ทดสอบ RingBuffer จำกัดขนาดหน่วยความจำแบบ FIFO")
    void testRingBufferMemorySafety() {
        RingBuffer<String> ringBuffer = new RingBuffer<>(5);

        for (int i = 1; i <= 8; i++) {
            ringBuffer.add("line-" + i);
        }

        assertEquals(5, ringBuffer.size());
        List<String> lines = ringBuffer.toList();
        assertEquals(5, lines.size());
        assertEquals("line-4", lines.get(0));
        assertEquals("line-5", lines.get(1));
        assertEquals("line-6", lines.get(2));
        assertEquals("line-7", lines.get(3));
        assertEquals("line-8", lines.get(4));
    }

    @Test
    @DisplayName("TASK-303: ทดสอบ ProcessExecutorService ดักจับและสตรีม Log ของกระบวนการ")
    void testProcessExecutorServiceRunsCommand() {
        ProcessExecutionResult result = processExecutorService.execute(
                Arrays.asList("hostname"),
                null,
                null,
                Duration.ofSeconds(10),
                null,
                null
        );

        assertNotNull(result);
        assertEquals(0, result.getExitCode());
        assertTrue(result.isSuccess());
        assertFalse(result.isTimedOut());
        assertFalse(result.getOutputAsText().trim().isEmpty(), "Hostname output should not be empty");
    }

    @Test
    @DisplayName("TASK-303: ทดสอบ Process Watchdog จัดการ Timeout และ Forcible Kill")
    void testProcessExecutorWatchdogKillsOnTimeout() {
        // Use ping with 10 count to exceed 500ms timeout
        ProcessExecutionResult result = processExecutorService.execute(
                Arrays.asList("ping", "-n", "10", "127.0.0.1"),
                null,
                null,
                Duration.ofMillis(500),
                null,
                null
        );

        assertNotNull(result);
        assertTrue(result.isTimedOut(), "Watchdog should mark process as timed out");
        assertFalse(result.isSuccess(), "Process should not be marked successful when timed out");
    }

    @Test
    @DisplayName("TASK-304: ทดสอบ REST APIs สำหรับจัดการ Credential Profile และการ Mask ข้อมูลความลับ")
    void testCredentialProfileLifecycle() {
        User adminUser = userRepository.findByUsername("admin").orElseThrow();
        UserPrincipal principal = UserPrincipal.create(adminUser);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();

        // 1. Create Profile
        CreateCredentialRequest createRequest = CreateCredentialRequest.builder()
                .name("Production-Postgres-Test")
                .description("Primary production DB")
                .credentialType(CredentialType.DATABASE_POSTGRESQL)
                .host("10.0.0.5")
                .port(5432)
                .username("postgres_admin")
                .databaseName("custos_prod")
                .secretPassword("TopSecretDatabasePassword#123")
                .extraMetadata("{\"sslMode\":\"require\"}")
                .build();

        CredentialResponse created = credentialService.createCredential(createRequest, principal, mockRequest);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Production-Postgres-Test", created.getName());
        assertEquals(CredentialType.DATABASE_POSTGRESQL, created.getCredentialType());
        // Secret must be masked in response
        assertEquals("********", created.getSecretMasked());

        // 2. Verify Internal Decrypted Secret can be retrieved securely
        DecryptedSecretPayload decryptedPayload = credentialService.getDecryptedSecret(created.getId());
        assertNotNull(decryptedPayload);
        assertEquals("TopSecretDatabasePassword#123", decryptedPayload.getPassword());

        // 3. Update Profile (without changing password)
        UpdateCredentialRequest updateRequest = UpdateCredentialRequest.builder()
                .name("Production-Postgres-Updated")
                .description("Updated description")
                .host("10.0.0.6")
                .port(5432)
                .username("postgres_admin")
                .databaseName("custos_prod")
                .secretPassword("********") // Keep existing
                .build();

        CredentialResponse updated = credentialService.updateCredential(created.getId(), updateRequest, principal, mockRequest);
        assertEquals("Production-Postgres-Updated", updated.getName());
        assertEquals("10.0.0.6", updated.getHost());

        // Password should still be preserved
        DecryptedSecretPayload afterUpdatePayload = credentialService.getDecryptedSecret(created.getId());
        assertEquals("TopSecretDatabasePassword#123", afterUpdatePayload.getPassword());

        // 4. Delete Profile
        credentialService.deleteCredential(created.getId(), principal, mockRequest);
        assertThrows(Exception.class, () -> credentialService.getCredentialById(created.getId()));
    }
}
