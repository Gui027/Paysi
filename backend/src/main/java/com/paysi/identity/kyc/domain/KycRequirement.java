package com.paysi.identity.kyc.domain;

import java.time.Instant;

public record KycRequirement(String code, String label, String status, String reason, Instant estimatedAt) {
}
