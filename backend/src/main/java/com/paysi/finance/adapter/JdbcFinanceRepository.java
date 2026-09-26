package com.paysi.finance.adapter;

import com.paysi.core.error.ConflictException;
import com.paysi.finance.app.FinanceModels.AccountRow;
import com.paysi.finance.app.FinanceModels.BankRow;
import com.paysi.finance.app.FinanceModels.PayoutRaw;
import com.paysi.finance.port.FinanceRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcFinanceRepository implements FinanceRepository {
    private final JdbcTemplate jdbc;

    JdbcFinanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AccountRow> account(UUID accountId) {
        return jdbc.query("SELECT full_name, person_type, tax_id, kyc_status, payout_delay FROM accounts WHERE id = ?",
                (rs, row) -> new AccountRow(rs.getString("full_name"), rs.getString("person_type"),
                        rs.getString("tax_id"), rs.getString("kyc_status"), rs.getString("payout_delay")),
                accountId).stream().findFirst();
    }

    @Override
    public Optional<BankRow> activeBank(UUID accountId) {
        return jdbc.query("""
                SELECT id, holder_name, pix_key_type, pix_key_enc, verified_at
                  FROM bank_accounts
                 WHERE account_id = ? AND archived_at IS NULL AND verified_at IS NOT NULL
                 ORDER BY created_at DESC, id DESC LIMIT 1
                """, (rs, row) -> new BankRow(rs.getObject("id", UUID.class), rs.getString("holder_name"),
                rs.getString("pix_key_type"), rs.getBytes("pix_key_enc"), instant(rs, "verified_at")),
                accountId).stream().findFirst();
    }

    @Override
    public List<UUID> activeBankIds(UUID accountId) {
        return jdbc.query("SELECT id FROM bank_accounts WHERE account_id = ? AND archived_at IS NULL",
                (rs, row) -> rs.getObject("id", UUID.class), accountId);
    }

    @Override
    public List<PayoutRaw> payouts(UUID accountId, int limit, int offset) {
        return jdbc.query("""
                SELECT p.id, p.created_at, p.amount_cents, p.status, p.receipt_url,
                       b.holder_name, b.pix_key_type, b.pix_key_enc
                  FROM payouts p JOIN bank_accounts b ON b.id = p.bank_account_id
                 WHERE p.account_id = ?
                 ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new PayoutRaw(rs.getObject("id", UUID.class), instant(rs, "created_at"),
                rs.getLong("amount_cents"), rs.getString("status"), rs.getString("holder_name"),
                rs.getString("pix_key_type"), rs.getBytes("pix_key_enc"), rs.getString("receipt_url")),
                accountId, limit, offset);
    }

    @Override
    public long countPayouts(UUID accountId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM payouts WHERE account_id = ?", Long.class, accountId);
        return total == null ? 0 : total;
    }

    @Override
    public void convertToCompany(UUID accountId, String legalName, String cnpj, String previousPersonType,
                                 String previousTaxId) {
        try {
            int changed = jdbc.update("""
                    UPDATE accounts SET person_type = 'PJ', tax_id = ?, full_name = ?, kyc_status = 'PENDING'
                     WHERE id = ? AND person_type = 'PF' AND status = 'ACTIVE'
                    """, cnpj, legalName, accountId);
            if (changed != 1) {
                throw new ConflictException("ACCOUNT_CONVERSION_UNAVAILABLE",
                        "Esta conta não pode ser alterada para CNPJ", null);
            }
        } catch (DuplicateKeyException error) {
            throw new ConflictException("TAX_ID_IN_USE", "Este CNPJ já está cadastrado na Paysi", "cnpj");
        }
        jdbc.update("""
                INSERT INTO account_conversions (id, account_id, previous_person_type, previous_tax_id, new_tax_id,
                                                 legal_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), accountId, previousPersonType, previousTaxId, cnpj, legalName,
                Timestamp.from(Instant.now()));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
