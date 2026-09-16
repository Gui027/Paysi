package com.paysi.billing.plan.domain;

import com.paysi.payment.split.Plan;

import java.time.Instant;
import java.util.UUID;

public record PlatformSubscription(
        UUID accountId,
        Plan plan,
        long priceCents,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        PlanStatus status,
        Instant pastDueSince,
        Plan pendingPlan,
        Long pendingPriceCents,
        Instant pendingEffectiveAt,
        String providerToken
) {
    public boolean hasPendingChange() {
        return pendingPlan != null;
    }
}
