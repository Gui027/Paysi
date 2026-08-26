package com.paysi.security.mfa.port;

import com.paysi.security.mfa.domain.MfaChallenge;
import com.paysi.security.mfa.domain.MfaCredential;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MfaStore {
    Optional<MfaCredential> credential(UUID accountId);
    void saveEnrollment(UUID accountId, byte[] encryptedSecret, List<String> recoveryHashes);
    void enable(UUID accountId, Instant enabledAt);
    boolean consumeRecoveryCode(UUID accountId, String hash);
    void saveChallenge(MfaChallenge challenge);
    Optional<MfaChallenge> lockChallenge(UUID challengeId);
    void incrementAttempts(UUID challengeId);
    void markVerified(UUID challengeId, Instant verifiedAt);
    boolean consumeVerified(UUID challengeId, UUID accountId, String operation, Instant now);
}
