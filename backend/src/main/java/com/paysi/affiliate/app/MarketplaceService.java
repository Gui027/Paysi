package com.paysi.affiliate.app;

import com.paysi.affiliate.port.AffiliateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MarketplaceService {
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final AffiliateRepository affiliates;

    public MarketplaceService(AffiliateRepository affiliates) {
        this.affiliates = affiliates;
    }

    @Transactional(readOnly = true)
    public MarketplacePage list(String rawCursor, Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(requestedLimit, MAX_LIMIT));
        List<MarketplaceItem> rows = affiliates.listMarketplace(AffiliateCursorCodec.decode(rawCursor), limit + 1);
        boolean hasMore = rows.size() > limit;
        List<MarketplaceItem> items = hasMore ? List.copyOf(rows.subList(0, limit)) : List.copyOf(rows);
        String next = hasMore ? AffiliateCursorCodec.encode(cursor(items.getLast())) : null;
        return new MarketplacePage(items, next);
    }

    private static AffiliateCursor cursor(MarketplaceItem item) {
        return new AffiliateCursor(item.createdAt(), item.productId());
    }
}
