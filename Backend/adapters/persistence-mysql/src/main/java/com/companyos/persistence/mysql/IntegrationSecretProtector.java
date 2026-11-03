package com.companyos.persistence.mysql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class IntegrationSecretProtector {
    private final String encodedKey;
    private final SecureRandom random = new SecureRandom();

    public IntegrationSecretProtector(@Value("${integration.secret-key:${notification.secret-key:}}") String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (Exception exception) { throw new IllegalStateException("Integration secret encryption failed", exception); }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new IllegalStateException("Integration secret decryption failed", exception); }
    }

    private SecretKeySpec key() {
        if (encodedKey == null || encodedKey.isBlank()) throw new IllegalStateException("INTEGRATION_SECRET_KEY is required to configure integrations");
        byte[] key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) throw new IllegalStateException("INTEGRATION_SECRET_KEY must be base64-encoded 32 bytes");
        return new SecretKeySpec(key, "AES");
    }
}
