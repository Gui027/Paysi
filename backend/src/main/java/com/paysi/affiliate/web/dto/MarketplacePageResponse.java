package com.paysi.affiliate.web.dto;

import com.paysi.affiliate.app.MarketplacePage;

import java.util.List;

public record MarketplacePageResponse(List<MarketplaceItemResponse> items, String nextCursor) {
    public static MarketplacePageResponse from(MarketplacePage page) {
        return new MarketplacePageResponse(page.items().stream().map(MarketplaceItemResponse::from).toList(),
                page.nextCursor());
    }
}
