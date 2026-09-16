package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.port.AffiliateAttributionRepository.LinkStats;

import java.util.UUID;

public record LinkStatsResponse(UUID affiliationId, UUID productId, String productName, String offerSlug,
                                 long clicks, long orders) {
    public static LinkStatsResponse from(LinkStats stats) {
        return new LinkStatsResponse(stats.affiliationId(), stats.productId(), stats.productName(),
                stats.offerSlug(), stats.clicks(), stats.orders());
    }
}
