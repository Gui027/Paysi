package com.paysi.risk.adapter;

import com.paysi.risk.port.RiskRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Repository
class JdbcRiskRepository implements RiskRepository {
    private final JdbcTemplate jdbc;

    JdbcRiskRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SellerMetrics sellerMetrics(UUID sellerId) {
        return jdbc.queryForObject("""
                SELECT
                  COALESCE((SELECT SUM(c.amount_cents) FROM charges c
                             JOIN orders o ON o.id = c.order_id
                             JOIN offers of ON of.id = o.offer_id
                             JOIN products p ON p.id = of.product_id
                            WHERE p.seller_id = ?
                              AND c.status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED','CHARGEBACK')), 0) AS volume,
                  COALESCE((SELECT SUM(d.amount_cents) FROM disputes d
                             JOIN charges c ON c.id = d.charge_id
                             JOIN orders o ON o.id = c.order_id
                             JOIN offers of ON of.id = o.offer_id
                             JOIN products p ON p.id = of.product_id
                            WHERE p.seller_id = ?), 0) AS disputed,
                  COALESCE((SELECT SUM(r.amount_cents) FROM refunds r
                             JOIN charges c ON c.id = r.charge_id
                             JOIN orders o ON o.id = c.order_id
                             JOIN offers of ON of.id = o.offer_id
                             JOIN products p ON p.id = of.product_id
                            WHERE p.seller_id = ? AND r.status = 'SUCCEEDED'), 0) AS refunded
                """, (rs, row) -> new SellerMetrics(rs.getLong("volume"), rs.getLong("disputed"),
                        rs.getLong("refunded")),
                sellerId, sellerId, sellerId);
    }

    @Override
    public PlatformMetrics platformMetrics() {
        return jdbc.queryForObject("""
                SELECT
                  COALESCE((SELECT SUM(amount_cents) FROM charges
                             WHERE status IN ('PAID','PARTIALLY_REFUNDED','REFUNDED','CHARGEBACK')), 0) AS volume,
                  COALESCE((SELECT SUM(amount_cents) FROM disputes), 0) AS disputed,
                  COALESCE((SELECT SUM(amount_cents) FROM refunds WHERE status = 'SUCCEEDED'), 0) AS refunded
                """, (rs, row) -> new PlatformMetrics(rs.getLong("volume"), rs.getLong("disputed"),
                        rs.getLong("refunded")));
    }

    @Override
    public void upsertAccountRisk(UUID accountId, long volumeCents, int chargebackBps, int refundBps,
                                   Instant computedAt) {
        jdbc.update("""
                INSERT INTO account_risk (account_id, volume_30d_cents, chargeback_bps, refund_bps, computed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET
                  volume_30d_cents = excluded.volume_30d_cents,
                  chargeback_bps = excluded.chargeback_bps,
                  refund_bps = excluded.refund_bps,
                  computed_at = excluded.computed_at
                """, accountId, volumeCents, chargebackBps, refundBps, Timestamp.from(computedAt));
    }

    @Override
    public void insertRiskEvent(UUID id, UUID accountId, String kind, String metric, int valueBps, int thresholdBps,
                                 String reason, Instant notifiedAt, Instant createdAt) {
        jdbc.update("""
                INSERT INTO risk_events (id, account_id, kind, metric, value_bps, threshold_bps, reason,
                                          notified_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, accountId, kind, metric, valueBps, thresholdBps, reason,
                notifiedAt == null ? null : Timestamp.from(notifiedAt), Timestamp.from(createdAt));
    }

    @Override
    public void upsertPlatformRiskIndex(LocalDate computedOn, int chargebackBps, int refundBps, long volumeCents,
                                         Instant computedAt) {
        jdbc.update("""
                INSERT INTO platform_risk_index (computed_on, chargeback_bps, refund_bps, volume_cents, computed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (computed_on) DO UPDATE SET
                  chargeback_bps = excluded.chargeback_bps,
                  refund_bps = excluded.refund_bps,
                  volume_cents = excluded.volume_cents,
                  computed_at = excluded.computed_at
                """, java.sql.Date.valueOf(computedOn), chargebackBps, refundBps, volumeCents,
                Timestamp.from(computedAt));
    }

    @Override
    public void updateAccountStatus(UUID accountId, String status) {
        jdbc.update("UPDATE accounts SET status = ? WHERE id = ?", status, accountId);
    }
}
