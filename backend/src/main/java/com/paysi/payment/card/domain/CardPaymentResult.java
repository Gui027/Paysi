package com.paysi.payment.card.domain;

import java.time.Instant;

public record CardPaymentResult(String providerChargeId, String status, CardThreeDs threeDs,
                                Instant pixAlternativeExpiresAt, boolean idempotentReplay) {
    public record CardThreeDs(boolean required, String status, String challengeUrl, String eci) {}
}
