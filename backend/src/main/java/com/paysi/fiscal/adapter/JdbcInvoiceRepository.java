package com.paysi.fiscal.adapter;

import com.paysi.fiscal.port.InvoiceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcInvoiceRepository implements InvoiceRepository {
    private final JdbcTemplate jdbc;

    JdbcInvoiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(UUID invoiceId, UUID chargeId, UUID sellerId) {
        jdbc.update("""
                INSERT INTO invoices (id, charge_id, issuer_id, status, amount_cents, attempt_count, created_at)
                SELECT ?, c.id, ?, 'QUEUED', c.amount_cents, 0, ?
                  FROM charges c
                 WHERE c.id = ?
                ON CONFLICT (charge_id) WHERE status <> 'FAILED' DO NOTHING
                """, invoiceId, sellerId, Timestamp.from(Instant.now()), chargeId);
    }

    @Override
    public Optional<ClaimedInvoice> claimDue(Instant now) {
        return jdbc.query("""
                SELECT id, charge_id, issuer_id, status, provider_ref, amount_cents, attempt_count
                  FROM invoices
                 WHERE status IN ('QUEUED', 'CANCEL_REQUESTED')
                   AND (next_retry_at IS NULL OR next_retry_at <= ?)
                 ORDER BY created_at
                 LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new ClaimedInvoice(rs.getObject("id", UUID.class),
                        rs.getObject("charge_id", UUID.class), rs.getObject("issuer_id", UUID.class),
                        rs.getString("status"), rs.getString("provider_ref"), rs.getLong("amount_cents"),
                        rs.getInt("attempt_count")),
                Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public boolean requestCancellation(UUID chargeId) {
        int rows = jdbc.update("""
                UPDATE invoices SET status = 'CANCEL_REQUESTED', next_retry_at = NULL
                 WHERE charge_id = ? AND status = 'ISSUED'
                """, chargeId);
        return rows > 0;
    }

    @Override
    public void markIssued(UUID invoiceId, String providerRef, String number, String pdfUrl, Instant issuedAt,
                            int attemptCount) {
        jdbc.update("""
                UPDATE invoices
                   SET status = 'ISSUED', provider_ref = ?, number = ?, pdf_url = ?, issued_at = ?,
                       attempt_count = ?, error = NULL, next_retry_at = NULL
                 WHERE id = ?
                """, providerRef, number, pdfUrl, Timestamp.from(issuedAt), attemptCount, invoiceId);
    }

    @Override
    public void markIssueRetry(UUID invoiceId, String error, int attemptCount, Instant nextRetryAt) {
        jdbc.update("""
                UPDATE invoices SET status = 'QUEUED', error = ?, attempt_count = ?, next_retry_at = ?
                 WHERE id = ?
                """, error, attemptCount, Timestamp.from(nextRetryAt), invoiceId);
    }

    @Override
    public void markIssueFailedTerminal(UUID invoiceId, String error, int attemptCount) {
        jdbc.update("""
                UPDATE invoices SET status = 'FAILED', error = ?, attempt_count = ?, next_retry_at = NULL
                 WHERE id = ?
                """, error, attemptCount, invoiceId);
    }

    @Override
    public void markCanceled(UUID invoiceId, int attemptCount) {
        jdbc.update("""
                UPDATE invoices SET status = 'CANCELED', attempt_count = ?, error = NULL, next_retry_at = NULL
                 WHERE id = ?
                """, attemptCount, invoiceId);
    }

    @Override
    public void markCancelRetry(UUID invoiceId, String error, int attemptCount, Instant nextRetryAt) {
        jdbc.update("""
                UPDATE invoices SET status = 'CANCEL_REQUESTED', error = ?, attempt_count = ?, next_retry_at = ?
                 WHERE id = ?
                """, error, attemptCount, Timestamp.from(nextRetryAt), invoiceId);
    }

    @Override
    public void markCancelFailedTerminal(UUID invoiceId, String error, int attemptCount) {
        jdbc.update("""
                UPDATE invoices SET status = 'CANCEL_FAILED', error = ?, attempt_count = ?, next_retry_at = NULL
                 WHERE id = ?
                """, error, attemptCount, invoiceId);
    }

    @Override
    public Optional<InvoiceView> findByChargeForSeller(UUID sellerId, UUID chargeId) {
        return jdbc.query("""
                SELECT id, charge_id, status, number, pdf_url, attempt_count, error
                  FROM invoices
                 WHERE charge_id = ? AND issuer_id = ?
                """, (rs, row) -> new InvoiceView(rs.getObject("id", UUID.class),
                        rs.getObject("charge_id", UUID.class), rs.getString("status"), rs.getString("number"),
                        rs.getString("pdf_url"), rs.getInt("attempt_count"), rs.getString("error")),
                chargeId, sellerId).stream().findFirst();
    }
}
