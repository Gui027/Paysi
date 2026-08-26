package com.paysi.identity.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {
    boolean existsByEmailAndStatusNot(String email, String status);

    boolean existsByTaxIdAndStatusNot(String taxId, String status);

    Optional<AccountEntity> findFirstByEmailAndStatusNot(String email, String status);
}
