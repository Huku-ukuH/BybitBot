package org.example.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class BybitRequestUtils {
    private BybitRequestUtils() {
    }

    public static String generateSignature(String secret, String payload) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secretKeySpec);
        byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
    public static long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }
}
