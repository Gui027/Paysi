package com.paysi.identity.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {
    boolean existsByEmailAndStatusNot(String email, String status);

    boolean existsByTaxIdAndStatusNot(String taxId, String status);
}
