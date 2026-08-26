package com.paysi.security.mfa.domain;

import java.time.Instant;

public record MfaCredential(byte[] encryptedSecret, Instant enabledAt) {
    public boolean enabled() { return enabledAt != null; }
}
