package com.paysi.identity.recovery.port;

import com.paysi.identity.recovery.domain.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenStore {
    void create(UUID accountId, String tokenHash, Instant expiresAt, Instant createdAt);
    Optional<PasswordResetToken> findForUpdate(String tokenHash);
    void markUsed(UUID tokenId, Instant usedAt);
}
