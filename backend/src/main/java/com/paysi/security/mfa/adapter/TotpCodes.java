package com.paysi.security.mfa.adapter;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Component
public class TotpCodes {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public byte[] newSecret() { byte[] value = new byte[20]; random.nextBytes(value); return value; }
    public String displaySecret(byte[] secret) { return encodeBase32(secret); }

    public boolean matches(byte[] secret, String supplied, Instant now) {
        if (supplied == null || !supplied.matches("\\d{6}")) return false;
        long counter = now.getEpochSecond() / 30;
        for (long candidate = counter - 1; candidate <= counter + 1; candidate++) {
            if (MessageDigest.isEqual(code(secret, candidate).getBytes(), supplied.getBytes())) return true;
        }
        return false;
    }

    String code(byte[] secret, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("TOTP calculation failed", exception);
        }
    }

    private static String encodeBase32(byte[] input) {
        StringBuilder result = new StringBuilder(); int buffer = 0; int bits = 0;
        for (byte value : input) { buffer = (buffer << 8) | (value & 0xff); bits += 8; while (bits >= 5) { result.append(BASE32[(buffer >> (bits - 5)) & 31]); bits -= 5; } }
        if (bits > 0) result.append(BASE32[(buffer << (5 - bits)) & 31]);
        return result.toString();
    }
}
