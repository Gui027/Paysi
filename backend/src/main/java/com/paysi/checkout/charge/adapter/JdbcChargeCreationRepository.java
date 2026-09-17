package com.paysi.checkout.charge.adapter;

import com.paysi.checkout.charge.port.ChargeCreationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcChargeCreationRepository implements ChargeCreationRepository {
    private final JdbcTemplate jdbc;

    JdbcChargeCreationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrderContext> findOrderContext(UUID orderId) {
        return jdbc.query("""
                SELECT p.seller_id, a.affiliate_id, a.commission_bps, o.paid_cents, o.method, o.installments,
                       b.name, b.email::text, b.person_type, b.tax_id, of.boleto_due_days, of.guarantee_days
                  FROM orders o
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  JOIN buyers b ON b.id = o.buyer_id
                  LEFT JOIN affiliations a ON a.id = o.affiliation_id AND a.status = 'APPROVED'
                 WHERE o.id = ?
                """, (rs, row) -> new OrderContext(rs.getObject("seller_id", UUID.class),
                        rs.getObject("affiliate_id", UUID.class),
                        rs.getObject("commission_bps") == null ? 0 : rs.getInt("commission_bps"),
                        rs.getLong("paid_cents"), rs.getString("method"), rs.getInt("installments"),
                        rs.getString("name"), rs.getString("email"), rs.getString("person_type"),
                        rs.getString("tax_id"), rs.getInt("boleto_due_days"), rs.getInt("guarantee_days")),
                orderId).stream().findFirst();
    }

    @Override
    public Optional<UUID> findChargeForOrder(UUID orderId) {
        return jdbc.query("SELECT id FROM charges WHERE order_id = ? ORDER BY created_at LIMIT 1",
                (rs, row) -> rs.getObject("id", UUID.class), orderId).stream().findFirst();
    }

    @Override
    public void insertCharge(UUID id, UUID orderId, long amountCents, String plan, int platformFeeBps,
                              long platformFeeFixedCents, long platformFeeCents, long affiliateFeeCents,
                              long sellerAmountCents, String status, Instant now) {
        jdbc.update("""
                INSERT INTO charges
                  (id, order_id, cycle_number, amount_cents, plan, platform_fee_bps, platform_fee_fixed_cents,
                   platform_fee_cents, affiliate_fee_cents, seller_amount_cents, status, attempt_count, created_at)
                VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
                """, id, orderId, amountCents, plan, platformFeeBps, platformFeeFixedCents, platformFeeCents,
                affiliateFeeCents, sellerAmountCents, status, Timestamp.from(now));
    }

    @Override
    public Optional<ChargeView> findChargeView(UUID chargeId) {
        return jdbc.query("""
                SELECT c.id, o.method, c.status, c.boleto_barcode, c.boleto_pdf_url, c.pix_qr_code,
                       c.payment_expires_at
                  FROM charges c JOIN orders o ON o.id = c.order_id
                 WHERE c.id = ?
                """, (rs, row) -> new ChargeView(rs.getObject("id", UUID.class), rs.getString("method"),
                        rs.getString("status"), rs.getString("boleto_barcode"), rs.getString("boleto_pdf_url"),
                        rs.getString("pix_qr_code"),
                        rs.getTimestamp("payment_expires_at") == null ? null
                                : rs.getTimestamp("payment_expires_at").toInstant()),
                chargeId).stream().findFirst();
    }
}
