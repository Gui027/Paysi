package com.paysi.subscription.domain;

import java.time.Instant;
import java.util.UUID;

public record Subscription(
        UUID id,
        UUID orderId,
        UUID offerId,
        SubscriptionStatus status,
        int cycleNumber,
        Instant trialEndsAt,
        Instant nextChargeAt,
        Instant canceledAt,
        String providerToken,
        Instant createdAt
) {
    /** Cancelamento foi pedido mas ainda não chegou o fim do ciclo vigente. */
    public boolean cancelPending() {
        return canceledAt != null && status != SubscriptionStatus.CANCELED;
    }
}
