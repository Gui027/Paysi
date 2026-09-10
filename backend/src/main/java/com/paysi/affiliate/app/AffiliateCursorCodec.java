package com.paysi.affiliate.app;

import com.paysi.core.error.ValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class AffiliateCursorCodec {
    private static final String VERSION = "1";
    private static final int MAX_LENGTH = 512;

    private AffiliateCursorCodec() {
    }

    static String encode(AffiliateCursor cursor) {
        String value = VERSION + "|" + cursor.createdAt() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    static AffiliateCursor decode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.length() > MAX_LENGTH) throw invalid();
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.US_ASCII);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw invalid();
            return new AffiliateCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException error) {
            throw invalid();
        }
    }

    private static ValidationException invalid() {
        return new ValidationException("AFFILIATE_CURSOR_INVALID", "Cursor de afiliação é inválido", "cursor");
    }
}
