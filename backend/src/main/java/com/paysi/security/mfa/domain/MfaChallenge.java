package com.paysi.security.mfa.domain;

import java.time.Instant;
import java.util.UUID;

public record MfaChallenge(UUID id, UUID accountId, SensitiveOperation operation, Instant expiresAt,
                           int attempts, Instant verifiedAt, Instant consumedAt) {
    public boolean expiredAt(Instant now) { return !now.isBefore(expiresAt); }
    public boolean available() { return consumedAt == null && attempts < 5; }
}
