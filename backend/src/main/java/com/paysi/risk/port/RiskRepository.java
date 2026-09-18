package com.paysi.risk.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface RiskRepository {

    /** Volume, contestações e reembolsos acumulados do vendedor (RF-076/RF-077). */
    SellerMetrics sellerMetrics(UUID sellerId);

    /** Volume, contestações e reembolsos agregados de toda a plataforma (RF-106). */
    PlatformMetrics platformMetrics();

    void upsertAccountRisk(UUID accountId, long volumeCents, int chargebackBps, int refundBps, Instant computedAt);

    void insertRiskEvent(UUID id, UUID accountId, String kind, String metric, int valueBps, int thresholdBps,
                          String reason, Instant notifiedAt, Instant createdAt);

    void upsertPlatformRiskIndex(LocalDate computedOn, int chargebackBps, int refundBps, long volumeCents,
                                  Instant computedAt);

    void updateAccountStatus(UUID accountId, String status);

    record SellerMetrics(long volumeCents, long disputedCents, long refundedCents) {
    }

    record PlatformMetrics(long volumeCents, long disputedCents, long refundedCents) {
    }
}
