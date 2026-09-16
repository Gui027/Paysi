package com.paysi.billing.plan.web.dto;

import com.paysi.billing.plan.domain.PlatformSubscription;

import java.time.Instant;

public record PlanResponse(
        String currentPlan,
        long monthlyFee,
        Instant currentPeriodStart,
        Instant nextBilling,
        String status,
        Instant pastDueSince,
        String pendingPlan,
        Long pendingMonthlyFee,
        Instant pendingEffectiveAt
) {
    public static PlanResponse from(PlatformSubscription subscription) {
        return new PlanResponse(subscription.plan().name(), subscription.priceCents(),
                subscription.currentPeriodStart(), subscription.currentPeriodEnd(), subscription.status().name(),
                subscription.pastDueSince(), subscription.pendingPlan() == null ? null : subscription.pendingPlan().name(),
                subscription.pendingPriceCents(), subscription.pendingEffectiveAt());
    }
}
