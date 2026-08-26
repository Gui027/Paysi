package com.paysi.security.mfa.app;
import com.paysi.security.mfa.domain.SensitiveOperation;
import java.time.Instant;
import java.util.UUID;
public record MfaChallengeView(UUID challengeId, SensitiveOperation operation, Instant expiresAt, boolean verified) { }
