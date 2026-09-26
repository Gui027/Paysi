package com.paysi.dashboard.adapter;

import com.paysi.dashboard.app.AffiliateDashboardView.AffiliationCounts;
import com.paysi.dashboard.app.AffiliateDashboardView.RecentCommission;
import com.paysi.dashboard.app.AffiliateDashboardView.TopProduct;
import com.paysi.dashboard.app.UpcomingReceivable;
import com.paysi.dashboard.port.AffiliateDashboardRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leituras do dashboard do afiliado. Toda soma em dinheiro é feita aqui, no banco. */
@Repository
class JdbcAffiliateDashboardRepository implements AffiliateDashboardRepository {
    private static final String FROM = """
              FROM charges c
              JOIN orders o ON o.id = c.order_id
              JOIN affiliations af ON af.id = o.affiliation_id
              JOIN offers f ON f.id = o.offer_id
              JOIN products p ON p.id = f.product_id
            """;
    /** Comissão da cobrança menos o que já foi devolvido da parte do afiliado. */
    private static final String NET = """
            (c.affiliate_fee_cents - COALESCE((SELECT SUM(r.affiliate_cents) FROM refunds r
                                                WHERE r.charge_id = c.id AND r.status = 'SUCCEEDED'), 0))
            """;
    private static final String APPROVED = " c.status IN ('PAID', 'PARTIALLY_REFUNDED') AND c.confirmed_at >= ? AND c.confirmed_at < ? ";

    private final JdbcTemplate jdbc;

    JdbcAffiliateDashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Sales sales(UUID affiliateId, Instant from, Instant to) {
        return jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(" + NET + "), 0) " + FROM + " WHERE af.affiliate_id = ? AND" + APPROVED,
                (rs, row) -> new Sales(rs.getLong(1), rs.getLong(2)), affiliateId, Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public long clicks(UUID affiliateId, Instant from, Instant to) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM affiliate_clicks k JOIN affiliations af ON af.id = k.affiliation_id
                 WHERE af.affiliate_id = ? AND k.created_at >= ? AND k.created_at < ?
                """, Long.class, affiliateId, Timestamp.from(from), Timestamp.from(to));
        return total == null ? 0 : total;
    }

    @Override
    public AffiliationCounts affiliations(UUID affiliateId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE status = 'APPROVED'), COUNT(*) FILTER (WHERE status = 'REQUESTED')
                  FROM affiliations WHERE affiliate_id = ?
                """, (rs, row) -> new AffiliationCounts(rs.getLong(1), rs.getLong(2)), affiliateId);
    }

    @Override
    public List<TopProduct> topProducts(UUID affiliateId, Instant from, Instant to, int limit) {
        return jdbc.query("SELECT p.id, p.name, COUNT(*) AS sales, COALESCE(SUM(" + NET + "), 0) AS commission " + FROM
                + " WHERE af.affiliate_id = ? AND" + APPROVED + " GROUP BY p.id, p.name ORDER BY commission DESC, p.name LIMIT ?",
                (rs, row) -> new TopProduct(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getLong(4)),
                affiliateId, Timestamp.from(from), Timestamp.from(to), limit);
    }

    @Override
    public List<RecentCommission> recentCommissions(UUID affiliateId, int limit) {
        return jdbc.query("SELECT c.id, p.name, " + NET + " AS commission, c.status, c.confirmed_at " + FROM
                + " WHERE af.affiliate_id = ? AND c.confirmed_at IS NOT NULL AND c.affiliate_fee_cents > 0"
                + " AND c.status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED', 'CHARGEBACK') ORDER BY c.confirmed_at DESC, c.id DESC LIMIT ?",
                (rs, row) -> new RecentCommission(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant()), affiliateId, limit);
    }

    @Override
    public List<UpcomingReceivable> upcomingCommissions(UUID affiliateId, Instant now, int limit) {
        return jdbc.query("""
                SELECT SUM(s.amount_cents), MIN(s.release_at)
                  FROM ledger_release_schedule s
                  JOIN ledger_entries e ON e.id = s.entry_id
                 WHERE s.account_id = ? AND s.released_at IS NULL AND s.release_at > ? AND e.origin = 'COMMISSION'
                 GROUP BY (s.release_at AT TIME ZONE 'America/Sao_Paulo')::date
                 ORDER BY 2 LIMIT ?
                """, (rs, row) -> new UpcomingReceivable(rs.getLong(1), rs.getTimestamp(2).toInstant()),
                affiliateId, Timestamp.from(now), limit);
    }
}
