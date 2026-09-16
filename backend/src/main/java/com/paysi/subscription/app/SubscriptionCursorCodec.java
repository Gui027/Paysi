package com.paysi.subscription.app;

import com.paysi.core.error.ValidationException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

final class SubscriptionCursorCodec {
    private static final String VERSION = "1";
    private static final int MAX_CURSOR_LENGTH = 512;

    private SubscriptionCursorCodec() {
    }

    static String encode(SubscriptionCursor cursor) {
        String value = VERSION + "|" + cursor.createdAt() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    static SubscriptionCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        if (cursor.length() > MAX_CURSOR_LENGTH) throw invalidCursor();
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) throw invalidCursor();
            return new SubscriptionCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | DateTimeParseException error) {
            throw invalidCursor();
        }
    }

    private static ValidationException invalidCursor() {
        return new ValidationException("SUBSCRIPTION_CURSOR_INVALID", "Cursor de assinaturas é inválido", "cursor");
    }
}
