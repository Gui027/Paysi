package com.paysi.affiliate.app;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;

import java.time.Instant;
import java.util.UUID;

public record MarketplaceItem(
        UUID productId,
        UUID sellerId,
        String product,
        String description,
        String seller,
        Segment segment,
        ChargeType chargeType,
        long startingPriceCents,
        Integer suggestedCommissionBps,
        int guaranteeDays,
        int payoutDelayDays,
        int attributionDays,
        Instant createdAt
) {
}
