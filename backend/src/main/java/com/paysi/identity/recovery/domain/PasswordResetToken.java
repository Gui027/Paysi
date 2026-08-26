package com.paysi.identity.recovery.domain;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(
        UUID id,
        UUID accountId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt
) {
    public boolean usableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }
}
