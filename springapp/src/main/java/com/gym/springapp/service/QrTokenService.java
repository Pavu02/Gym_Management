package com.gym.springapp.service;

import com.gym.springapp.dto.QrTokenResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class QrTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long ROTATION_PERIOD_SECONDS = 30;
    private static final long MAX_TOKEN_AGE_SECONDS = 60;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public QrTokenService(@Value("${jwt.secret:GymManagementSecretKeyForJwtTokenGeneration2026SecureKey}") String secret) {
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
    }

    public QrTokenResponseDTO generateRotatingToken() {
        long now = Instant.now().getEpochSecond();
        byte[] saltBytes = new byte[8];
        secureRandom.nextBytes(saltBytes);
        String salt = Base64.getUrlEncoder().withoutPadding().encodeToString(saltBytes);

        String payload = now + ":" + salt;
        String signature = sign(payload);
        String fullToken = Base64.getUrlEncoder().withoutPadding().encodeToString((payload + ":" + signature).getBytes(StandardCharsets.UTF_8));

        return new QrTokenResponseDTO(fullToken, ROTATION_PERIOD_SECONDS, now);
    }

    public boolean verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token.trim());
            String text = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = text.split(":");
            if (parts.length != 3) {
                return false;
            }

            long timestamp = Long.parseLong(parts[0]);
            String salt = parts[1];
            String expectedSig = parts[2];

            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > MAX_TOKEN_AGE_SECONDS) {
                return false;
            }

            String computedSig = sign(timestamp + ":" + salt);
            return computedSig.equals(expectedSig);
        } catch (Exception e) {
            return false;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error signing QR token: " + e.getMessage(), e);
        }
    }
}
