package com.paysi.admin.dashboard.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminDashboardView(Summary summary, List<RevenuePoint> revenueTimeline,
                                 List<AccountRow> accounts, int page, int size,
                                 long totalAccounts, int totalPages) {
    public record Summary(long totalRevenueCents, long periodRevenueCents, long paidSales,
                          long activeSubscriptions, long newAccounts) { }

    public record RevenuePoint(LocalDate date, long revenueCents, long sales) { }

    public record AccountRow(UUID id, String name, String email, String status,
                             long revenueCents, long paidSales, Instant createdAt) { }
}
