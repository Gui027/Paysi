package com.paysi.dashboard.app;

import com.paysi.ledger.query.app.BalanceView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dashboard do modo afiliado: comissões, cliques, conversão, saldo e os produtos que mais renderam. */
public record AffiliateDashboardView(
        DashboardPeriod period,
        DashboardBlock<Earnings> earnings,
        DashboardBlock<BalanceView> balance,
        DashboardBlock<List<UpcomingReceivable>> nextReceivables,
        DashboardBlock<AffiliationCounts> affiliations,
        DashboardBlock<List<DashboardAlert>> alerts,
        DashboardBlock<List<TopProduct>> topProducts,
        DashboardBlock<List<RecentCommission>> recentCommissions) {

    /** {@code conversionPercent}: vendas por clique, em porcentagem com uma casa ("3.4"); nulo sem cliques. */
    public record Earnings(long commissionCents, long sales, long clicks, String conversionPercent) { }

    public record AffiliationCounts(long active, long pending) { }

    public record TopProduct(UUID productId, String productName, long sales, long commissionCents) { }

    public record RecentCommission(UUID id, String productName, long commissionCents, String status, Instant occurredAt) { }
}
