package com.paysi.identity.session.app;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.domain.UserSession;

import java.time.Instant;
import java.util.UUID;

public record SessionView(UUID accountId, InitialMode activeMode, Instant lastActivityAt, Instant expiresAt) {
    static SessionView from(UserSession session) {
        return new SessionView(session.accountId(), session.activeMode(), session.lastActivityAt(), session.expiresAt());
    }
}
