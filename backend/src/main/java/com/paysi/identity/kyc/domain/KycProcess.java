package com.paysi.identity.kyc.domain;

import java.time.Instant;
import java.util.List;

public record KycProcess(String providerProcessId, String providerUrl, Instant expiresAt,
                         List<KycRequirement> requirements) {
    public boolean activeAt(Instant instant) { return instant.isBefore(expiresAt); }
}
