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
        int attributionDays,
        Long maxCommissionCents
) {
    public static MarketplaceItemResponse from(MarketplaceItem item) {
        return new MarketplaceItemResponse(item.productId(), item.product(), item.description(), item.seller(),
                item.segment(), item.chargeType(), item.startingPriceCents(), item.suggestedCommissionBps(),
                item.guaranteeDays(), item.payoutDelayDays(), item.attributionDays(), maxCommission(item));
    }

    /** Quanto o afiliado recebe, no máximo, por venda ao preço inicial (arredonda para baixo). */
    private static Long maxCommission(MarketplaceItem item) {
        Integer bps = item.suggestedCommissionBps();
        return bps == null ? null : Math.multiplyExact(item.startingPriceCents(), (long) bps) / 10_000;
    }
}
