package com.custos.modules.vault.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class VaultService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ObjectMapper objectMapper;

    @Value("${custos.vault.master-key:${CUSTOS_VAULT_MASTER_KEY:}}")
    private String masterKeyProperty;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (masterKeyProperty == null || masterKeyProperty.trim().isEmpty()) {
            throw new IllegalStateException("Master Vault Key is not set! Set CUSTOS_VAULT_MASTER_KEY in environment.");
        }
        this.secretKey = deriveSecretKey(masterKeyProperty.trim());
        log.info("VaultService initialized successfully with AES-256-GCM encryption.");
    }

    private SecretKey deriveSecretKey(String rawKey) {
        byte[] keyBytes;
        // Try decoding 64 hex characters (32 bytes = 256 bits)
        if (rawKey.length() == 64 && rawKey.matches("^[0-9a-fA-F]{64}$")) {
            keyBytes = HexFormat.of().parseHex(rawKey);
        } else {
            try {
                byte[] decoded = Base64.getDecoder().decode(rawKey);
                if (decoded.length == 32) {
                    keyBytes = decoded;
                } else {
                    keyBytes = hashSha256(rawKey.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IllegalArgumentException e) {
                keyBytes = hashSha256(rawKey.getBytes(StandardCharsets.UTF_8));
            }
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }

    private byte[] hashSha256(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Encrypts plaintext using AES-256-GCM with a unique 12-byte IV.
     * Returns Base64 string containing: [12-byte IV] + [Ciphertext with 128-bit Auth Tag]
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt data with AES-256-GCM: {}", e.getMessage());
            throw new SecurityException("Vault encryption failure", e);
        }
    }

    /**
     * Decrypts AES-256-GCM Base64 ciphertext. Verifies integrity tag.
     */
    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null) {
            return null;
        }

        try {
            byte[] cipherTextWithIv = Base64.getDecoder().decode(cipherTextBase64);
            if (cipherTextWithIv.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Invalid ciphertext length");
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherTextWithIv);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt data with AES-256-GCM: {}", e.getMessage());
            throw new SecurityException("Vault decryption failure: Invalid key or tampered data", e);
        }
    }

    /**
     * Serializes an object to JSON and encrypts it.
     */
    public String encryptJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(object);
            return encrypt(json);
        } catch (Exception e) {
            throw new SecurityException("Failed to serialize and encrypt object", e);
        }
    }

    /**
     * Decrypts ciphertext and deserializes it from JSON into the target class.
     */
    public <T> T decryptJson(String cipherTextBase64, Class<T> targetClass) {
        if (cipherTextBase64 == null) {
            return null;
        }
        try {
            String json = decrypt(cipherTextBase64);
            return objectMapper.readValue(json, targetClass);
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt and deserialize object", e);
        }
    }
}
