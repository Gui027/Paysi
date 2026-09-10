package com.paysi.affiliate.app;

import java.util.List;

public record MarketplacePage(List<MarketplaceItem> items, String nextCursor) {
}
