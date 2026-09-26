package com.paysi.dashboard.port;

import com.paysi.dashboard.app.AffiliateDashboardView.AffiliationCounts;
import com.paysi.dashboard.app.AffiliateDashboardView.RecentCommission;
import com.paysi.dashboard.app.AffiliateDashboardView.TopProduct;
import com.paysi.dashboard.app.UpcomingReceivable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AffiliateDashboardRepository {
    /** Vendas aprovadas no período e a comissão líquida de reembolsos. */
    Sales sales(UUID affiliateId, Instant from, Instant to);

    long clicks(UUID affiliateId, Instant from, Instant to);

    AffiliationCounts affiliations(UUID affiliateId);

    List<TopProduct> topProducts(UUID affiliateId, Instant from, Instant to, int limit);

    List<RecentCommission> recentCommissions(UUID affiliateId, int limit);

    /** Comissões que ainda vão liberar, somadas por dia. */
    List<UpcomingReceivable> upcomingCommissions(UUID affiliateId, Instant now, int limit);

    record Sales(long count, long commissionCents) { }
}
