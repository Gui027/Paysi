package com.paysi.identity.recovery.adapter;

import com.paysi.identity.recovery.domain.PasswordResetToken;
import com.paysi.identity.recovery.port.PasswordResetTokenStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaPasswordResetTokenStore implements PasswordResetTokenStore {
    private final PasswordResetTokenJpaRepository repository;

    JpaPasswordResetTokenStore(PasswordResetTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void create(UUID accountId, String tokenHash, Instant expiresAt, Instant createdAt) {
        repository.saveAndFlush(new PasswordResetTokenEntity(UUID.randomUUID(), accountId, tokenHash,
                expiresAt, createdAt));
    }

    @Override
    public Optional<PasswordResetToken> findForUpdate(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public void markUsed(UUID tokenId, Instant usedAt) {
        PasswordResetTokenEntity entity = repository.findById(tokenId)
                .orElseThrow(() -> new IllegalStateException("Token de recuperação não encontrado"));
        entity.markUsed(usedAt);
        repository.saveAndFlush(entity);
    }
}
