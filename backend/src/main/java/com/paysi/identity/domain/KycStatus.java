package com.paysi.identity.domain;

/** Estado da verificação de identidade. Nasce {@link #PENDING} — RF-006 nunca dispara no cadastro. */
public enum KycStatus {
    PENDING,
    SUBMITTED,
    APPROVED,
    REJECTED
}
