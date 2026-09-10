package com.paysi.dashboard.adapter;

import com.paysi.dashboard.app.DashboardAlert;
import com.paysi.dashboard.app.RecentSale;
import com.paysi.dashboard.app.SalesSummary;
import com.paysi.dashboard.app.SubscriptionSummary;
import com.paysi.dashboard.app.UpcomingReceivable;
import com.paysi.dashboard.port.DashboardQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcDashboardQueryRepository implements DashboardQueryRepository {
    private final JdbcTemplate jdbc;

    public JdbcDashboardQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SalesSummary sales(UUID sellerId, Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.amount_cents), 0), COUNT(*)
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers f ON f.id = o.offer_id
                  JOIN products p ON p.id = f.product_id
                 WHERE p.seller_id = ?
                   AND c.confirmed_at >= ?
                   AND c.confirmed_at < ?
                   AND c.confirmed_at IS NOT NULL
                """, (rs, row) -> new SalesSummary(rs.getLong(1), rs.getLong(2)),
                sellerId, Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public List<UpcomingReceivable> upcomingReceivables(UUID sellerId, Instant now, int limit) {
        return jdbc.query("""
                SELECT r.seller_amount_cents, r.expected_at
                  FROM receivables r
                  JOIN charges c ON c.id = r.charge_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers f ON f.id = o.offer_id
                  JOIN products p ON p.id = f.product_id
                 WHERE p.seller_id = ?
                   AND r.settled_at IS NULL
                   AND r.expected_at > ?
                 ORDER BY r.expected_at ASC, r.id ASC
                 LIMIT ?
                """, (rs, row) -> new UpcomingReceivable(rs.getLong(1), rs.getTimestamp(2).toInstant()),
                sellerId, Timestamp.from(now), limit);
    }

    @Override
    public SubscriptionSummary subscriptions(UUID sellerId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE s.status = 'ACTIVE'),
                       COUNT(*) FILTER (WHERE s.status = 'PAST_DUE')
                  FROM subscriptions s
                  JOIN offers f ON f.id = s.offer_id
                  JOIN products p ON p.id = f.product_id
                 WHERE p.seller_id = ?
                """, (rs, row) -> new SubscriptionSummary(rs.getLong(1), rs.getLong(2)), sellerId);
    }

    @Override
    public List<RecentSale> recentSales(UUID sellerId, int limit) {
        return jdbc.query("""
                SELECT c.id, b.name, c.amount_cents, o.method, c.status, c.confirmed_at
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN buyers b ON b.id = o.buyer_id
                  JOIN offers f ON f.id = o.offer_id
                  JOIN products p ON p.id = f.product_id
                 WHERE p.seller_id = ?
                   AND c.confirmed_at IS NOT NULL
                 ORDER BY c.confirmed_at DESC, c.id DESC
                 LIMIT ?
                """, (rs, row) -> new RecentSale(
                rs.getObject(1, UUID.class), maskName(rs.getString(2)), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()), sellerId, limit);
    }

    @Override
    public List<DashboardAlert> accountAlerts(UUID sellerId) {
        var alerts = new java.util.ArrayList<DashboardAlert>();
        jdbc.query("""
                SELECT a.kyc_status, kp.provider_url
                  FROM accounts a
                  LEFT JOIN kyc_processes kp ON kp.account_id = a.id
                 WHERE a.id = ?
                """, (ResultSetExtractor<Void>) rs -> {
            if (!rs.next()) return null;
            String status = rs.getString(1);
            String providerUrl = rs.getString(2);
            switch (status) {
                case "PENDING" -> alerts.add(new DashboardAlert("kyc", "warning",
                        "Verificação de identidade pendente",
                        "Inicie a verificação para poder publicar ofertas e receber pagamentos.", providerUrl));
                case "SUBMITTED" -> alerts.add(new DashboardAlert("kyc", "warning",
                        "Verificação em análise",
                        "Seus documentos estão em análise pelo provedor. Isso pode levar alguns dias.", providerUrl));
                case "REJECTED" -> alerts.add(new DashboardAlert("kyc", "danger",
                        "Verificação de identidade recusada",
                        "Revise os requisitos pendentes e reenvie os documentos.", providerUrl));
                default -> { }
            }
            return null;
        }, sellerId);

        jdbc.query("""
                SELECT EXISTS (
                    SELECT 1 FROM products p
                    JOIN offers f ON f.product_id = p.id
                   WHERE p.seller_id = ? AND p.segment = 'SAAS'
                     AND p.archived_at IS NULL AND f.archived_at IS NULL
                ),
                EXISTS (
                    SELECT 1 FROM fiscal_profiles fp
                   WHERE fp.account_id = ? AND fp.validated_at IS NOT NULL
                )
                """, (ResultSetExtractor<Void>) rs -> {
            if (rs.next() && rs.getBoolean(1) && !rs.getBoolean(2)) {
                alerts.add(new DashboardAlert("fiscal", "warning",
                        "Perfil fiscal pendente",
                        "Complete e valide o perfil fiscal para vender produtos SaaS.", "/fiscal"));
            }
            return null;
        }, sellerId, sellerId);

        jdbc.query("""
                SELECT ar.tier, re.reason
                  FROM accounts a
                  LEFT JOIN account_risk ar ON ar.account_id = a.id
                  LEFT JOIN LATERAL (
                      SELECT reason FROM risk_events
                       WHERE account_id = a.id
                       ORDER BY created_at DESC LIMIT 1
                  ) re ON TRUE
                 WHERE a.id = ?
                """, (ResultSetExtractor<Void>) rs -> {
            if (rs.next() && rs.getInt(1) > 0) {
                String reason = rs.getString(2);
                alerts.add(new DashboardAlert("risk", "danger",
                        "Sua conta exige atenção de risco",
                        reason == null || reason.isBlank() ? "Há uma revisão de risco pendente na conta." : reason,
                        "/conta"));
            }
            return null;
        }, sellerId);

        return List.copyOf(alerts);
    }

    static String maskName(String name) {
        if (name == null || name.isBlank()) return "Comprador";
        String trimmed = name.strip();
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) return trimmed.substring(0, 1) + "***";
        return trimmed.substring(0, 1) + "*** " + trimmed.substring(firstSpace + 1, firstSpace + 2) + "***";
    }
}
