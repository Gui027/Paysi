package com.paysi.identity.session.domain;

import com.paysi.identity.domain.InitialMode;

import java.time.Instant;
import java.util.UUID;

public record UserSession(
        UUID accountId,
        InitialMode activeMode,
        Instant lastActivityAt,
        Instant expiresAt
) {
    public boolean expiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UserSession renew(Instant now, Instant newExpiration) {
        return new UserSession(accountId, activeMode, now, newExpiration);
    }

    public UserSession switchMode(InitialMode mode, Instant now, Instant newExpiration) {
        return new UserSession(accountId, mode, now, newExpiration);
    }
}
