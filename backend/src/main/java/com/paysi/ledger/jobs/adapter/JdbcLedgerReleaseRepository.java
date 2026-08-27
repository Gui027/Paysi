package com.paysi.ledger.jobs.adapter;

import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Origin;
import com.paysi.ledger.jobs.domain.DueRelease;
import com.paysi.ledger.jobs.port.LedgerReleaseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcLedgerReleaseRepository implements LedgerReleaseRepository {
    private final JdbcTemplate jdbc;

    public JdbcLedgerReleaseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DueRelease> claimNext(Bucket bucket, Instant now) {
        return jdbc.query("""
                        select s.entry_id,s.account_id,s.bucket,s.amount_cents,e.origin,t.created_at,
                               greatest(s.release_at,coalesce((
                                 select max(r.expected_at) from receivables r
                                  where (t.reference_type='RECEIVABLE' and r.id::text=t.reference_id)
                                     or (t.reference_type='CHARGE' and r.charge_id::text=t.reference_id)
                               ),s.release_at)),a.payout_delay
                          from ledger_release_schedule s
                          join ledger_entries e on e.id=s.entry_id
                          join ledger_transactions t on t.id=e.transaction_id
                          join accounts a on a.id=s.account_id
                         where s.released_at is null and s.release_at<=? and s.bucket=?
                         order by s.release_at,s.entry_id
                         limit 1 for update of s skip locked
                        """, (rs, row) -> new DueRelease(rs.getLong(1), rs.getObject(2, UUID.class),
                        Bucket.valueOf(rs.getString(3)), rs.getLong(4), Origin.valueOf(rs.getString(5)),
                        rs.getTimestamp(6).toInstant(), rs.getTimestamp(7).toInstant(), rs.getString(8)),
                Timestamp.from(now), bucket.name()).stream().findFirst();
    }

    @Override
    public long lockAndReadDebt(UUID accountId) {
        jdbc.query("select pg_advisory_xact_lock(4210,hashtext(?))", rs -> null, accountId.toString());
        Long balance = jdbc.queryForObject("""
                select coalesce(sum(case when direction='CREDIT' then amount_cents else -amount_cents end),0)
                  from ledger_entries where account_id=? and bucket='DEBT'
                """, Long.class, accountId);
        return Math.max(0, -(balance == null ? 0 : balance));
    }

    @Override
    public boolean markReleased(long entryId, UUID transactionId, Instant releasedAt) {
        return jdbc.update("""
                        update ledger_release_schedule set released_at=?,release_transaction_id=?
                         where entry_id=? and released_at is null
                        """, Timestamp.from(releasedAt), transactionId, entryId) == 1;
    }
}
