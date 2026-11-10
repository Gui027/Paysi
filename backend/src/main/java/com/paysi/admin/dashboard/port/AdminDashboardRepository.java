package com.paysi.admin.dashboard.port;

import com.paysi.admin.dashboard.domain.AdminDashboardView;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AdminDashboardRepository {
    void saveSession(String tokenHash, String username, Instant now, Instant expiresAt);
    Optional<StoredSession> findSession(String tokenHash);
    void renewSession(String tokenHash, Instant now, Instant expiresAt);
    void deleteSession(String tokenHash);
    void deleteExpiredSessions(Instant now);

    AdminDashboardView.Summary summary(Instant periodStart);
    List<AdminDashboardView.RevenuePoint> revenueTimeline(LocalDate firstDay, LocalDate lastDay);
    List<AdminDashboardView.AccountRow> accounts(String query, int limit, int offset);
    long countAccounts(String query);

    record StoredSession(String username, Instant expiresAt) { }
}
