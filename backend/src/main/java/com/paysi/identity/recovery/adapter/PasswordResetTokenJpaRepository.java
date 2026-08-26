package com.paysi.identity.recovery.adapter;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
}
