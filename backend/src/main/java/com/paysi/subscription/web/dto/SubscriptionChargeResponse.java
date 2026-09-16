package com.paysi.subscription.web.dto;

import com.paysi.subscription.domain.SubscriptionCharge;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionChargeResponse(
        UUID id,
        int cycleNumber,
        long amountCents,
        String status,
        int attemptCount,
        Instant nextRetryAt,
        Instant paidAt,
        Instant createdAt
) {
    public static SubscriptionChargeResponse from(SubscriptionCharge charge) {
        return new SubscriptionChargeResponse(charge.id(), charge.cycleNumber(), charge.amountCents(),
                charge.status(), charge.attemptCount(), charge.nextRetryAt(), charge.paidAt(), charge.createdAt());
    }
}
