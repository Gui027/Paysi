package com.paysi.reconciliation.adapter;

import com.paysi.reconciliation.port.ReconciliationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
class JdbcReconciliationRepository implements ReconciliationRepository {
    private final JdbcTemplate jdbc;

    JdbcReconciliationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<InternalTotal> internalTotals(LocalDate date) {
        return jdbc.query("""
                SELECT c.provider_charge_id AS reference, c.amount_cents, p.seller_id
                  FROM charges c
                  JOIN orders o ON o.id = c.order_id
                  JOIN offers of ON of.id = o.offer_id
                  JOIN products p ON p.id = of.product_id
                 WHERE c.provider_charge_id IS NOT NULL AND c.paid_at::date = ?
                """, (rs, row) -> new InternalTotal(rs.getString("reference"), rs.getLong("amount_cents"),
                        rs.getObject("seller_id", UUID.class)), Date.valueOf(date));
    }

    @Override
    public List<StatementLine> statementEntries(LocalDate date) {
        return jdbc.query("""
                SELECT provider_reference, amount_cents, statement_date
                  FROM provider_statement_entries
                 WHERE statement_date = ?
                """, (rs, row) -> new StatementLine(rs.getString("provider_reference"), rs.getLong("amount_cents"),
                        rs.getDate("statement_date").toLocalDate()), Date.valueOf(date));
    }

    @Override
    public void importStatementLines(List<StatementLine> lines, Instant importedAt) {
        for (StatementLine line : lines) {
            jdbc.update("""
                    INSERT INTO provider_statement_entries (id, provider_reference, amount_cents, statement_date, imported_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (provider_reference) DO UPDATE SET
                      amount_cents = EXCLUDED.amount_cents,
                      statement_date = EXCLUDED.statement_date,
                      imported_at = EXCLUDED.imported_at
                    """, UUID.randomUUID(), line.providerReference(), line.amountCents(),
                    Date.valueOf(line.statementDate()), Timestamp.from(importedAt));
        }
    }

    @Override
    public UpsertResult upsert(LocalDate date, String providerReference, long internalCents, long providerCents,
                                long differenceCents, String status, Instant now) {
        return jdbc.query("""
                INSERT INTO reconciliation_entries
                  (id, recon_date, provider_reference, internal_cents, provider_cents, difference_cents, status,
                   created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (recon_date, provider_reference) DO UPDATE SET
                  internal_cents = EXCLUDED.internal_cents,
                  provider_cents = EXCLUDED.provider_cents,
                  difference_cents = EXCLUDED.difference_cents,
                  status = EXCLUDED.status,
                  updated_at = EXCLUDED.updated_at
                RETURNING id, alerted_at AS previous_alerted_at
                """, this::mapUpsertResult, UUID.randomUUID(), Date.valueOf(date), providerReference, internalCents,
                providerCents, differenceCents, status, Timestamp.from(now), Timestamp.from(now)).get(0);
    }

    @Override
    public void markAlerted(UUID entryId, Instant alertedAt) {
        jdbc.update("UPDATE reconciliation_entries SET alerted_at = ? WHERE id = ?", Timestamp.from(alertedAt),
                entryId);
    }

    private UpsertResult mapUpsertResult(ResultSet rs, int row) throws SQLException {
        Timestamp previous = rs.getTimestamp("previous_alerted_at");
        return new UpsertResult(rs.getObject("id", UUID.class), previous == null ? null : previous.toInstant());
    }
}
