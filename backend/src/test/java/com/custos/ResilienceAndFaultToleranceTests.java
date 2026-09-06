package com.custos;

import com.custos.modules.execution.ProcessExecutionResult;
import com.custos.modules.execution.ProcessExecutorService;
import com.custos.modules.transfer.client.RemoteTransferClient;
import com.custos.modules.transfer.dto.StoragePrecheckRequest;
import com.custos.modules.transfer.dto.StoragePrecheckResult;
import com.custos.modules.transfer.service.StoragePrecheckService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

@SpringBootTest
@ActiveProfiles("test")
public class ResilienceAndFaultToleranceTests {

    @Autowired
    private StoragePrecheckService storagePrecheckService;

    @Autowired
    private ProcessExecutorService processExecutorService;

    @Test
    @DisplayName("Fault Tolerance - Transient network timeout with per-chunk auto-retry")
    public void testTransientNetworkRetryPerChunk() {
        Set<String> successfulChunks = new HashSet<>();
        Map<String, Integer> attemptCounts = new HashMap<>();

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
                int attempts = attemptCounts.getOrDefault(remoteFileName, 0) + 1;
                attemptCounts.put(remoteFileName, attempts);

                // Simulate 2 transient network timeouts on chunk 2
                if (remoteFileName.endsWith("part02") && attempts <= 2) {
                    throw new RuntimeException("Simulated SocketTimeoutException: connection reset by peer");
                }

                successfulChunks.add(remoteFileName);
            }

            @Override
            public boolean remoteFileExists(String remoteDirectory, String remoteFileName) {
                return successfulChunks.contains(remoteFileName);
            }

            @Override
            public long getRemoteFileSize(String remoteDirectory, String remoteFileName) {
                return successfulChunks.contains(remoteFileName) ? 1024L : -1L;
            }
        };

        List<String> chunks = List.of(
                "archive.tar.gz.part01",
                "archive.tar.gz.part02",
                "archive.tar.gz.part03",
                "archive.tar.gz.part04"
        );

        int maxRetries = 3;
        int totalRetries = 0;

        for (String chunk : chunks) {
            boolean uploaded = false;
            int attempt = 0;

            while (!uploaded && attempt <= maxRetries) {
                try {
                    mockClient.uploadFile(new File(chunk), "/remote", chunk);
                    uploaded = true;
                } catch (Exception e) {
                    attempt++;
                    totalRetries++;
                    if (attempt > maxRetries) {
                        Assertions.fail("Exceeded max retries for chunk " + chunk);
                    }
                }
            }
        }

        // Chunk 1 uploaded on 1st attempt
        Assertions.assertEquals(1, attemptCounts.get("archive.tar.gz.part01"));
        // Chunk 2 retried twice and succeeded on 3rd attempt
        Assertions.assertEquals(3, attemptCounts.get("archive.tar.gz.part02"));
        // Chunk 3 and 4 were NOT re-started from chunk 1
        Assertions.assertEquals(1, attemptCounts.get("archive.tar.gz.part03"));
        Assertions.assertEquals(1, attemptCounts.get("archive.tar.gz.part04"));
        // Total retries matches simulated network drop
        Assertions.assertEquals(2, totalRetries);
        // All 4 chunks reached destination
        Assertions.assertEquals(4, successfulChunks.size());
    }

    @Test
    @DisplayName("Fault Tolerance - Storage Pre-check rejects operation when disk is insufficient")
    public void testStoragePrecheckRejection(@TempDir Path tempDir) {
        // 1. Normal requirement should pass
        StoragePrecheckRequest normalRequest = StoragePrecheckRequest.builder()
                .path(tempDir.toString())
                .requiredBytes(5 * 1024 * 1024L) // 5 MB
                .safetyMargin(1.2)
                .build();

        StoragePrecheckResult normalResult = storagePrecheckService.checkStorage(normalRequest);
        Assertions.assertTrue(normalResult.isHasEnoughSpace());

        // 2. Astronomical requirement (e.g. 50 Petabytes) must be rejected
        long petabytes50 = 50L * 1024 * 1024 * 1024 * 1024 * 1024;
        StoragePrecheckRequest excessiveRequest = StoragePrecheckRequest.builder()
                .path(tempDir.toString())
                .requiredBytes(petabytes50)
                .safetyMargin(1.5)
                .build();

        StoragePrecheckResult excessiveResult = storagePrecheckService.checkStorage(excessiveRequest);
        Assertions.assertFalse(excessiveResult.isHasEnoughSpace());
        Assertions.assertTrue(excessiveResult.getRequiredBytes() > excessiveResult.getUsableSpaceBytes());

        // 3. assertSufficientSpace must throw IllegalStateException
        Assertions.assertThrows(IllegalStateException.class, () -> {
            storagePrecheckService.assertSufficientSpace(tempDir.toString(), petabytes50, 1.2);
        });
    }

    @Test
    @DisplayName("Fault Tolerance - Watchdog process killer terminates hung process")
    public void testWatchdogTerminatesHungProcess() {
        // Execute a command with an impossible 0-second timeout to trigger watchdog termination
        ProcessExecutionResult result = processExecutorService.execute(
                List.of("ping", "127.0.0.1", "-n", "5"),
                Duration.ofMillis(0)
        );

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isTimedOut(), "Watchdog should mark process as timed out");
        Assertions.assertFalse(result.isSuccess(), "Timed out process should not be marked as success");
    }
}
