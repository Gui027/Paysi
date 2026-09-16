package com.paysi.billing.plan.port;

import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.payment.split.Plan;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository {

    Optional<PlatformSubscription> find(UUID accountId);

    void schedulePendingChange(UUID accountId, Plan pendingPlan, long pendingPriceCents, Instant effectiveAt,
                                String providerToken);

    void insertHistory(UUID id, UUID accountId, String fromPlan, String toPlan, UUID changedBy,
                        String priceTable, Instant now);

    List<PlanChangeRecord> listHistory(UUID accountId, int limit);

    /** Períodos vencidos (rollover ou aplicação de mudança pendente). */
    Optional<PlatformSubscription> claimDueRollover(Instant now);

    void applyRollover(UUID accountId, String plan, long priceCents, Instant periodStart, Instant periodEnd,
                        String status, Instant pastDueSince);

    /** PAST_DUE há mais de 10 dias (RF-102): rebaixamento automático para TRANSACIONAL. */
    Optional<UUID> claimDueDowngrade(Instant now);

    void applyDowngrade(UUID accountId, Instant periodStart, Instant periodEnd);

    AccountBillingInfo billingInfo(UUID accountId);

    record PlanChangeRecord(UUID id, String fromPlan, String toPlan, String priceTable, Instant createdAt) {
    }

    record AccountBillingInfo(String name, String email, String personType, String taxId) {
    }
}
