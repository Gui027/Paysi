package com.paysi.identity.domain;

/** RF-117: uma conta {@link #CLOSED} libera e-mail e documento para novo cadastro. */
public enum AccountStatus {
    ACTIVE,
    LIMITED,
    SUSPENDED,
    CLOSED
}
