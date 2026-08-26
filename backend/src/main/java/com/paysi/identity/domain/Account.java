package com.paysi.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma conta Paysi. Sem Spring, sem banco — a persistência mora no adaptador (identity.adapter).
 */
public final class Account {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final PersonType personType;
    private final TaxId taxId;
    private final KycStatus kycStatus;
    private final PayoutDelay payoutDelay;
    private final int riskTier;
    private final AccountStatus status;
    private final Instant createdAt;

    private Account(UUID id, String email, String passwordHash, String fullName, PersonType personType,
                     TaxId taxId, KycStatus kycStatus, PayoutDelay payoutDelay, int riskTier,
                     AccountStatus status, Instant createdAt) {
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

    /**
     * Toda conta nova nasce com KYC pendente (RF-006 só dispara na primeira publicação),
     * prazo de recebimento padrão D+32, risco 0 e status ativo.
     */
    public static Account createNew(String normalizedEmail, String passwordHash, String fullName,
                                     PersonType personType, TaxId taxId) {
        Objects.requireNonNull(normalizedEmail, "normalizedEmail");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(personType, "personType");
        Objects.requireNonNull(taxId, "taxId");
        return new Account(UUID.randomUUID(), normalizedEmail, passwordHash, fullName, personType,
                taxId, KycStatus.PENDING, PayoutDelay.D32, 0, AccountStatus.ACTIVE, Instant.now());
    }

    /** Reidrata uma conta já persistida — usada pelo adaptador ao ler do banco. */
    public static Account reconstitute(UUID id, String email, String passwordHash, String fullName,
                                        PersonType personType, TaxId taxId, KycStatus kycStatus,
                                        PayoutDelay payoutDelay, int riskTier, AccountStatus status,
                                        Instant createdAt) {
        return new Account(id, email, passwordHash, fullName, personType, taxId, kycStatus,
                payoutDelay, riskTier, status, createdAt);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public PersonType personType() {
        return personType;
    }

    public TaxId taxId() {
        return taxId;
    }

    public KycStatus kycStatus() {
        return kycStatus;
    }

    public PayoutDelay payoutDelay() {
        return payoutDelay;
    }

    public int riskTier() {
        return riskTier;
    }

    public AccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
