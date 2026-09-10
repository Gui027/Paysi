package com.paysi.dashboard.port;

import com.paysi.dashboard.app.DashboardAlert;
import com.paysi.dashboard.app.RecentSale;
import com.paysi.dashboard.app.SalesSummary;
import com.paysi.dashboard.app.SubscriptionSummary;
import com.paysi.dashboard.app.UpcomingReceivable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DashboardQueryRepository {
    SalesSummary sales(UUID sellerId, Instant from, Instant to);

    List<UpcomingReceivable> upcomingReceivables(UUID sellerId, Instant now, int limit);

    SubscriptionSummary subscriptions(UUID sellerId);

    List<RecentSale> recentSales(UUID sellerId, int limit);

    List<DashboardAlert> accountAlerts(UUID sellerId);
}
