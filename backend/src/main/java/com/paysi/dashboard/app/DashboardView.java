package com.paysi.dashboard.app;

import com.paysi.ledger.query.app.BalanceView;

import java.util.List;

public record DashboardView(
        DashboardPeriod period,
        DashboardBlock<SalesSummary> salesToday,
        DashboardBlock<BalanceView> balance,
        DashboardBlock<List<UpcomingReceivable>> nextReceivables,
        DashboardBlock<SubscriptionSummary> subscriptions,
        DashboardBlock<List<DashboardAlert>> alerts,
        DashboardBlock<List<RecentSale>> recentSales) {
}
