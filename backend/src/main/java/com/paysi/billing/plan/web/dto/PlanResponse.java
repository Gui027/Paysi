package com.paysi.billing.plan.web.dto;

import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.domain.PriceTable;
import com.paysi.payment.split.Plan;

import java.time.Instant;
import java.util.Map;

public record PlanResponse(
        String currentPlan,
        long monthlyFee,
        Instant currentPeriodStart,
        Instant nextBilling,
        String status,
        Instant pastDueSince,
        String pendingPlan,
        Long pendingMonthlyFee,
        Instant pendingEffectiveAt,
        Map<String, Long> priceTable
) {
    public static PlanResponse from(PlatformSubscription subscription) {
        return new PlanResponse(subscription.plan().name(), subscription.priceCents(),
                subscription.currentPeriodStart(), subscription.currentPeriodEnd(), subscription.status().name(),
                subscription.pastDueSince(), subscription.pendingPlan() == null ? null : subscription.pendingPlan().name(),
                subscription.pendingPriceCents(), subscription.pendingEffectiveAt(),
                Map.of(Plan.TRANSACIONAL.name(), PriceTable.priceFor(Plan.TRANSACIONAL),
                        Plan.ESCALA.name(), PriceTable.priceFor(Plan.ESCALA)));
    }
}
