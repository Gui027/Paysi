package com.paysi.checkout.dispute.adapter;

import com.paysi.checkout.dispute.port.DisputeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcDisputeRepository implements DisputeRepository {
    private final JdbcTemplate jdbc;

    JdbcDisputeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ChargeDisputeContext> lockChargeForDispute(UUID sellerId, UUID chargeId) {
        return jdbc.query("""
                SELECT p.seller_id, af.affiliate_id, c.amount_cents, c.refunded_cents, c.status,
                       c.seller_amount_cents, c.affiliate_fee_cents, c.platform_fee_cents, c.provider_fee_cents
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                 WHERE c.id = ? AND p.seller_id = ?
                 FOR UPDATE OF c
                """, (rs, row) -> new ChargeDisputeContext(rs.getObject("seller_id", UUID.class),
                        rs.getObject("affiliate_id", UUID.class), rs.getLong("amount_cents"),
                        rs.getLong("refunded_cents"), rs.getString("status"), rs.getLong("seller_amount_cents"),
                        rs.getLong("affiliate_fee_cents"), rs.getLong("platform_fee_cents"),
                        rs.getObject("provider_fee_cents") == null ? null : rs.getLong("provider_fee_cents")),
                chargeId, sellerId).stream().findFirst();
    }

    @Override
    public Optional<StoredDispute> findByProviderDisputeId(String providerDisputeId) {
        return jdbc.query("""
                SELECT d.id, d.charge_id, p.seller_id, af.affiliate_id, d.amount_cents, d.acquirer_fee_cents,
                       d.reason, d.status, d.deadline_at, d.provider_dispute_id, d.created_at
                  FROM disputes d
                  JOIN charges c ON c.id = d.charge_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                 WHERE d.provider_dispute_id = ?
                """, JdbcDisputeRepository::toStoredDispute, providerDisputeId).stream().findFirst();
    }

    @Override
    public Optional<StoredDispute> lockDisputeForResolution(UUID sellerId, UUID disputeId) {
        return jdbc.query("""
                SELECT d.id, d.charge_id, p.seller_id, af.affiliate_id, d.amount_cents, d.acquirer_fee_cents,
                       d.reason, d.status, d.deadline_at, d.provider_dispute_id, d.created_at
                  FROM disputes d
                  JOIN charges c ON c.id = d.charge_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                 WHERE d.id = ? AND p.seller_id = ?
                 FOR UPDATE OF d
                """, JdbcDisputeRepository::toStoredDispute, disputeId, sellerId).stream().findFirst();
    }

    @Override
    public Optional<StoredDispute> findDisputeForSeller(UUID sellerId, UUID disputeId) {
        return jdbc.query("""
                SELECT d.id, d.charge_id, p.seller_id, af.affiliate_id, d.amount_cents, d.acquirer_fee_cents,
                       d.reason, d.status, d.deadline_at, d.provider_dispute_id, d.created_at
                  FROM disputes d
                  JOIN charges c ON c.id = d.charge_id
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                  LEFT JOIN affiliations af ON af.id = o.affiliation_id
                 WHERE d.id = ? AND p.seller_id = ?
                """, JdbcDisputeRepository::toStoredDispute, disputeId, sellerId).stream().findFirst();
    }

    @Override
    public boolean insertDispute(UUID id, UUID chargeId, long amountCents, long acquirerFeeCents, String reason,
                                  String status, Instant deadlineAt, String providerDisputeId, Instant createdAt) {
        int rows = jdbc.update("""
                INSERT INTO disputes (id, charge_id, kind, reason, amount_cents, acquirer_fee_cents, status,
                                       deadline_at, provider_dispute_id, created_at)
                VALUES (?, ?, 'CHARGEBACK', ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider_dispute_id) DO NOTHING
                """, id, chargeId, reason, amountCents, acquirerFeeCents, status, Timestamp.from(deadlineAt),
                providerDisputeId, Timestamp.from(createdAt));
        return rows > 0;
    }

    @Override
    public void updateDisputeStatus(UUID disputeId, String status) {
        jdbc.update("UPDATE disputes SET status = ? WHERE id = ?", status, disputeId);
    }

    @Override
    public void applyChargeDisputeStatus(UUID chargeId, String status) {
        jdbc.update("UPDATE charges SET status = ? WHERE id = ?", status, chargeId);
    }

    @Override
    public List<BucketAmount> sellerOpeningAllocation(UUID disputeId) {
        return jdbc.query("""
                SELECT le.bucket, le.amount_cents
                  FROM ledger_entries le
                  JOIN ledger_transactions lt ON lt.id = le.transaction_id
                 WHERE lt.type = 'CHARGEBACK' AND lt.reference_type = 'DISPUTE'
                   AND lt.reference_id = ? AND le.direction = 'DEBIT' AND le.bucket <> 'SYSTEM'
                """, (rs, row) -> new BucketAmount(rs.getString("bucket"), rs.getLong("amount_cents")),
                disputeId + ":seller");
    }

    @Override
    public List<BucketAmount> affiliateOpeningAllocation(UUID disputeId) {
        return jdbc.query("""
                SELECT le.bucket, le.amount_cents
                  FROM ledger_entries le
                  JOIN ledger_transactions lt ON lt.id = le.transaction_id
                 WHERE lt.type = 'CHARGEBACK' AND lt.reference_type = 'DISPUTE'
                   AND lt.reference_id = ? AND le.direction = 'DEBIT' AND le.bucket <> 'SYSTEM'
                """, (rs, row) -> new BucketAmount(rs.getString("bucket"), rs.getLong("amount_cents")),
                disputeId + ":affiliate");
    }

    @Override
    public Optional<SaleEvidenceSnapshot> findEvidence(UUID chargeId) {
        return jdbc.query("""
                SELECT charge_id, ip::text AS ip, user_agent, device_key, terms_hash, terms_accepted_at,
                       three_ds_result, email_delivered_at, email_opened_at, access_log::text AS access_log
                  FROM sale_evidence
                 WHERE charge_id = ?
                """, (rs, row) -> new SaleEvidenceSnapshot(rs.getObject("charge_id", UUID.class), rs.getString("ip"),
                        rs.getString("user_agent"), rs.getString("device_key"), rs.getString("terms_hash"),
                        instant(rs.getTimestamp("terms_accepted_at")), rs.getString("three_ds_result"),
                        instant(rs.getTimestamp("email_delivered_at")), instant(rs.getTimestamp("email_opened_at")),
                        rs.getString("access_log")),
                chargeId).stream().findFirst();
    }

    @Override
    public void recordEmailDelivered(UUID chargeId, Instant deliveredAt) {
        jdbc.update("UPDATE sale_evidence SET email_delivered_at = ? WHERE charge_id = ?",
                Timestamp.from(deliveredAt), chargeId);
    }

    @Override
    public void recordEmailOpened(UUID chargeId, Instant openedAt) {
        jdbc.update("UPDATE sale_evidence SET email_opened_at = ? WHERE charge_id = ?",
                Timestamp.from(openedAt), chargeId);
    }

    @Override
    public void appendAccessLog(UUID chargeId, String entryJson) {
        jdbc.update("UPDATE sale_evidence SET access_log = access_log || ?::jsonb WHERE charge_id = ?",
                "[" + entryJson + "]", chargeId);
    }

    private static StoredDispute toStoredDispute(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new StoredDispute(rs.getObject("id", UUID.class), rs.getObject("charge_id", UUID.class),
                rs.getObject("seller_id", UUID.class), rs.getObject("affiliate_id", UUID.class),
                rs.getLong("amount_cents"), rs.getLong("acquirer_fee_cents"), rs.getString("reason"),
                rs.getString("status"), instant(rs.getTimestamp("deadline_at")), rs.getString("provider_dispute_id"),
                instant(rs.getTimestamp("created_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
