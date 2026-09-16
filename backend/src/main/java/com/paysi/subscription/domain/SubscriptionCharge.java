package com.paysi.subscription.domain;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionCharge(
        UUID id,
        int cycleNumber,
        long amountCents,
        String status,
        int attemptCount,
        Instant nextRetryAt,
        Instant paidAt,
        Instant createdAt
) {
}
