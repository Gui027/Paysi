package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.app.MarketplaceItem;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;

import java.util.UUID;

public record MarketplaceItemResponse(
        UUID productId,
        String product,
        String description,
        String seller,
        Segment segment,
        ChargeType chargeType,
        long startingPriceCents,
        Integer suggestedCommissionBps,
        int guaranteeDays,
        int payoutDelayDays,
        int attributionDays
) {
    public static MarketplaceItemResponse from(MarketplaceItem item) {
        return new MarketplaceItemResponse(item.productId(), item.product(), item.description(), item.seller(),
                item.segment(), item.chargeType(), item.startingPriceCents(), item.suggestedCommissionBps(),
                item.guaranteeDays(), item.payoutDelayDays(), item.attributionDays());
    }
}
