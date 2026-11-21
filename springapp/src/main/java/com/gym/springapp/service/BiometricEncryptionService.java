package com.gym.springapp.service;

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
import java.util.List;

@Service
public class BiometricEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final int IV_LENGTH = 12; // 12 bytes for GCM

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public BiometricEncryptionService(@Value("${gym.biometric.aes-key:GymManagementDefaultBiometricSecretAESKey2026SecureGCM}") String fallbackKey) {
        String envKey = System.getenv("BIOMETRIC_ENCRYPTION_KEY");
        String rawKey = (envKey != null && !envKey.trim().isEmpty()) ? envKey.trim() : fallbackKey;
        this.secretKey = deriveKey(rawKey);
    }

    private SecretKey deriveKey(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public String encryptDescriptor(List<Double> descriptor) {
        if (descriptor == null || descriptor.size() != 128) {
            throw new IllegalArgumentException("Descriptor must contain exactly 128 numbers");
        }

        try {
            // Convert 128 floats (stored as Double) into a 512-byte buffer
            ByteBuffer buffer = ByteBuffer.allocate(128 * 4);
            for (Double d : descriptor) {
                if (d == null || !Double.isFinite(d)) {
                    throw new IllegalArgumentException("Descriptor values must be finite numbers");
                }
                buffer.putFloat(d.floatValue());
            }
            byte[] plaintext = buffer.array();

            // Generate fresh random 12-byte IV
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            // Output format: IV (12 bytes) + Ciphertext + Tag (appended by GCM)
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting face descriptor: " + e.getMessage(), e);
        }
    }

    public float[] decryptDescriptor(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.trim().isEmpty()) {
            throw new IllegalArgumentException("Encrypted descriptor cannot be empty");
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid encrypted payload length");
            }

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);

            int ciphertextLen = combined.length - IV_LENGTH;
            byte[] ciphertext = new byte[ciphertextLen];
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertextLen);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(ciphertext);
            if (decrypted.length != 128 * 4) {
                throw new IllegalStateException("Decrypted biometric descriptor has invalid length: " + decrypted.length);
            }

            ByteBuffer buffer = ByteBuffer.wrap(decrypted);
            float[] descriptor = new float[128];
            for (int i = 0; i < 128; i++) {
                descriptor[i] = buffer.getFloat();
            }
            return descriptor;
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting face descriptor: " + e.getMessage(), e);
        }
    }

    public double calculateEuclideanDistance(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != 128 || v2.length != 128) {
            throw new IllegalArgumentException("Both descriptors must be 128-float arrays");
        }

        double sum = 0.0;
        for (int i = 0; i < 128; i++) {
            double diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public double calculateEuclideanDistance(List<Double> v1, float[] v2) {
        if (v1 == null || v2 == null || v1.size() != 128 || v2.length != 128) {
            throw new IllegalArgumentException("Both descriptors must be 128-dimensional vectors");
        }

        double sum = 0.0;
        for (int i = 0; i < 128; i++) {
            double diff = v1.get(i) - v2[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}
