package com.paysi.affiliate.port;

import com.paysi.affiliate.app.AffiliateCursor;
import com.paysi.affiliate.app.MarketplaceItem;
import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationEndReason;
import com.paysi.affiliate.domain.AffiliationRecurrence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AffiliateRepository {
    List<MarketplaceItem> listMarketplace(AffiliateCursor cursor, int limit);

    Optional<MarketplaceItem> findMarketplaceProduct(UUID productId);

    Affiliation request(UUID affiliationId, UUID productId, UUID affiliateId, Instant now);

    Optional<Affiliation> find(UUID affiliationId);

    List<Affiliation> listForAffiliate(UUID affiliateId, AffiliateCursor cursor, int limit);

    List<Affiliation> listForSeller(UUID sellerId, AffiliateCursor cursor, int limit);

    Optional<Affiliation> approve(UUID affiliationId, UUID sellerId, int commissionBps,
                                  AffiliationRecurrence recurrence, Instant now);

    Optional<Affiliation> end(UUID affiliationId, UUID accountId, AffiliationEndReason reason, Instant now);
}
