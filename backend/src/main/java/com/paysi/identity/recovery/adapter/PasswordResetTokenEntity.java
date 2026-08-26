package com.paysi.identity.recovery.adapter;

import com.paysi.identity.recovery.domain.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetTokenEntity {
    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordResetTokenEntity() { }

    PasswordResetTokenEntity(UUID id, UUID accountId, String tokenHash, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    PasswordResetToken toDomain() {
        return new PasswordResetToken(id, accountId, tokenHash, expiresAt, usedAt, createdAt);
    }

    void markUsed(Instant instant) {
        this.usedAt = instant;
    }
}
