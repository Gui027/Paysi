package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.domain.AffiliationStatus;

import java.time.Instant;
import java.util.UUID;

public record AffiliationResponse(
        UUID id,
        UUID productId,
        String product,
        UUID affiliateId,
        String affiliate,
        String seller,
        int commissionBps,
        AffiliationRecurrence recurrence,
        AffiliationStatus status,
        AffiliationEndReason endedReason,
        Instant approvedAt,
        Instant endedAt,
        Instant createdAt
) {
    public static AffiliationResponse from(Affiliation affiliation) {
        return new AffiliationResponse(affiliation.id(), affiliation.productId(), affiliation.productName(),
                affiliation.affiliateId(), affiliation.affiliateName(), affiliation.sellerName(),
                affiliation.commissionBps(), affiliation.recurrence(), affiliation.status(),
                affiliation.endedReason(), affiliation.approvedAt(), affiliation.endedAt(),
                affiliation.createdAt());
    }
}
