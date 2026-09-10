package com.paysi.affiliate.domain;

import java.time.Instant;
import java.util.UUID;

public record Affiliation(
        UUID id,
        UUID productId,
        String productName,
        UUID sellerId,
        String sellerName,
        UUID affiliateId,
        String affiliateName,
        int commissionBps,
        AffiliationRecurrence recurrence,
        AffiliationStatus status,
        AffiliationEndReason endedReason,
        Instant approvedAt,
        Instant endedAt,
        Instant createdAt
) {
}
