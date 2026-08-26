package com.paysi.security.mfa.adapter;

import com.paysi.security.mfa.port.SecretProtector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AesGcmSecretProtector implements SecretProtector {
    private static final int IV_LENGTH = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretProtector(@Value("${paysi.mfa.encryption-key:}") String encodedKey) {
        byte[] keyBytes = encodedKey.isBlank() ? random.generateSeed(32) : Base64.getDecoder().decode(encodedKey);
        if (keyBytes.length != 32) throw new IllegalArgumentException("MFA encryption key must have 32 bytes");
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public byte[] encrypt(byte[] clear) {
        byte[] iv = random.generateSeed(IV_LENGTH);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, clear, iv);
        byte[] result = Arrays.copyOf(iv, iv.length + encrypted.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    @Override
    public byte[] decrypt(byte[] encrypted) {
        if (encrypted.length <= IV_LENGTH) throw new IllegalArgumentException("Invalid encrypted MFA secret");
        return crypt(Cipher.DECRYPT_MODE, Arrays.copyOfRange(encrypted, IV_LENGTH, encrypted.length), Arrays.copyOf(encrypted, IV_LENGTH));
    }

    private byte[] crypt(int mode, byte[] input, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("MFA secret cryptography failed", exception);
        }
    }
}
