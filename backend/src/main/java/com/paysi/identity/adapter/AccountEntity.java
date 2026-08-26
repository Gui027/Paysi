package com.paysi.identity.adapter;

import com.paysi.identity.domain.Account;
import com.paysi.identity.domain.AccountStatus;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PayoutDelay;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Mapeamento JPA de {@code accounts} (V001). Só os campos que este cadastro escreve/lê. */
@Entity
@Table(name = "accounts")
class AccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "person_type", nullable = false)
    private String personType;

    @Column(name = "tax_id", nullable = false)
    private String taxId;

    @Column(name = "kyc_status", nullable = false)
    private String kycStatus;

    @Column(name = "payout_delay", nullable = false)
    private String payoutDelay;

    @Column(name = "risk_tier", nullable = false)
    private int riskTier;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountEntity() {
        // exigido pelo JPA
    }

    private AccountEntity(UUID id, String email, String passwordHash, String fullName, String personType,
                           String taxId, String kycStatus, String payoutDelay, int riskTier, String status,
                           Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.personType = personType;
        this.taxId = taxId;
        this.kycStatus = kycStatus;
        this.payoutDelay = payoutDelay;
        this.riskTier = riskTier;
        this.status = status;
        this.createdAt = createdAt;
    }

    static AccountEntity fromDomain(Account account) {
        return new AccountEntity(
                account.id(),
                account.email(),
                account.passwordHash(),
                account.fullName(),
                account.personType().name(),
                account.taxId().digits(),
                account.kycStatus().name(),
                account.payoutDelay().name(),
                account.riskTier(),
                account.status().name(),
                account.createdAt());
    }

    Account toDomain() {
        return Account.reconstitute(id, email, passwordHash, fullName, PersonType.valueOf(personType),
                new TaxId(taxId), KycStatus.valueOf(kycStatus), PayoutDelay.valueOf(payoutDelay), riskTier,
                AccountStatus.valueOf(status), createdAt);
    }

    void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }
}
