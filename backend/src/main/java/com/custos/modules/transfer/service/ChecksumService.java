package com.custos.modules.transfer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
public class ChecksumService {

    private static final int BUFFER_SIZE = 65536; // 64 KB streaming buffer

    public String calculateSha256(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("Target file must exist and be a valid file: " + file);
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        } catch (Exception e) {
            log.error("Failed to compute SHA-256 for file {}: {}", file.getAbsolutePath(), e.getMessage());
            throw new RuntimeException("Checksum calculation failed: " + e.getMessage(), e);
        }
    }

    public boolean verifyChecksum(File file, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.trim().isEmpty()) {
            return false;
        }
        String actualSha256 = calculateSha256(file);
        boolean matches = actualSha256.equalsIgnoreCase(expectedSha256.trim());
        if (!matches) {
            log.warn("Checksum mismatch for {}: expected {}, actual {}",
                    file.getName(), expectedSha256, actualSha256);
        }
        return matches;
    }
}
