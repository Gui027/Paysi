package com.paysi.checkout.refund.adapter;

import com.paysi.checkout.refund.port.RefundRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcRefundRepository implements RefundRepository {
    private final JdbcTemplate jdbc;

    JdbcRefundRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeRefundContext> lockChargeForRefund(UUID sellerId, UUID chargeId) {
        return jdbc.query("""
                SELECT p.seller_id, af.affiliate_id, c.provider_charge_id, c.amount_cents, c.refunded_cents,
                       c.status, c.seller_amount_cents, c.affiliate_fee_cents, c.platform_fee_cents,
                       c.provider_fee_cents
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                 WHERE c.id = ? AND p.seller_id = ?
                 FOR UPDATE OF c
                """, (rs, row) -> new ChargeRefundContext(rs.getObject("seller_id", UUID.class),
                        rs.getObject("affiliate_id", UUID.class), rs.getString("provider_charge_id"),
                        rs.getLong("amount_cents"), rs.getLong("refunded_cents"), rs.getString("status"),
                        rs.getLong("seller_amount_cents"), rs.getLong("affiliate_fee_cents"),
                        rs.getLong("platform_fee_cents"),
                        rs.getObject("provider_fee_cents") == null ? null : rs.getLong("provider_fee_cents")),
                chargeId, sellerId).stream().findFirst();
    }

    @Override
    public Optional<StoredRefund> findByIdempotencyKey(UUID chargeId, String idempotencyKey) {
        return jdbc.query("""
                SELECT r.id, r.amount_cents, r.seller_cents, r.affiliate_cents, r.platform_cents, r.provider_cents,
                       r.status, c.refunded_cents, c.status AS charge_status
                  FROM refunds r
                  JOIN charges c ON c.id = r.charge_id
                 WHERE r.charge_id = ? AND r.idempotency_key = ?
                """, (rs, row) -> new StoredRefund(rs.getObject("id", UUID.class), rs.getLong("amount_cents"),
                        rs.getLong("seller_cents"), rs.getLong("affiliate_cents"), rs.getLong("platform_cents"),
                        rs.getLong("provider_cents"), rs.getString("status"), rs.getLong("refunded_cents"),
                        rs.getString("charge_status")),
                chargeId, idempotencyKey).stream().findFirst();
    }

    @Override
    public boolean insertRefund(UUID id, UUID chargeId, long amountCents, long sellerCents, long affiliateCents,
                                 long platformCents, long providerCents, String reason, String status,
                                 String providerRefundId, String idempotencyKey, String requestedBy,
                                 Instant createdAt, Instant settledAt) {
        int rows = jdbc.update("""
                INSERT INTO refunds
                  (id, charge_id, amount_cents, seller_cents, affiliate_cents, platform_cents, provider_cents,
                   reason, status, provider_refund_id, idempotency_key, requested_by, created_at, settled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (charge_id, idempotency_key) DO NOTHING
                """, id, chargeId, amountCents, sellerCents, affiliateCents, platformCents, providerCents, reason,
                status, providerRefundId, idempotencyKey, requestedBy, Timestamp.from(createdAt),
                settledAt == null ? null : Timestamp.from(settledAt));
        return rows > 0;
    }

    @Override
    public void applyChargeRefund(UUID chargeId, long newRefundedCents, String newStatus) {
        jdbc.update("UPDATE charges SET refunded_cents = ?, status = ? WHERE id = ?",
                newRefundedCents, newStatus, chargeId);
    }
}
