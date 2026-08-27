package com.paysi.ledger.adapter;

import com.paysi.ledger.domain.*;
import com.paysi.ledger.port.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Supplier;

@Repository
public class JdbcLedgerRepository implements LedgerRepository {
    private final JdbcTemplate jdbc;
    public JdbcLedgerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public <T> T withAccountLocks(Collection<UUID> ids, Supplier<T> work) {
        ids.stream().sorted().forEach(id -> jdbc.query("select pg_advisory_xact_lock(4210, hashtext(?))", rs -> null, id.toString()));
        return work.get();
    }
    @Override public Optional<StoredLedgerTransaction> find(TransactionType type, LedgerReference ref) {
        return jdbc.query("select id,command_hash from ledger_transactions where type=? and reference_type=? and reference_id=?",
                (rs,row)->new StoredLedgerTransaction(rs.getObject(1,UUID.class),rs.getString(2)),type.name(),ref.type().name(),ref.id()).stream().findFirst();
    }
    @Override public long rawBalance(UUID account, Bucket bucket) {
        Long value=jdbc.queryForObject("select coalesce(sum(case when direction='CREDIT' then amount_cents else -amount_cents end),0) from ledger_entries where account_id=? and bucket=?",Long.class,account,bucket.name());
        return value==null?0:value;
    }
    @Override public Optional<UUID> tryInsertTransaction(LedgerCommand command,String hash) {
        UUID id=UUID.randomUUID();
        return jdbc.query("insert into ledger_transactions(id,type,reference_type,reference_id,description,command_hash) values (?,?,?,?,?,?) on conflict(type,reference_type,reference_id) do nothing returning id",
                (rs,row)->rs.getObject(1,UUID.class),id,command.type().name(),command.reference().type().name(),command.reference().id(),command.description(),hash).stream().findFirst();
    }
    @Override public void insertEntries(UUID tx,List<LedgerEntry> entries) {
        entries.forEach(entry->jdbc.update("insert into ledger_entries(transaction_id,account_id,bucket,direction,amount_cents,origin,release_at) values (?,?,?,?,?,?,?)",
                tx,entry.accountId(),entry.bucket().name(),entry.direction().name(),entry.amountCents(),entry.origin().name(),entry.releaseAt()==null?null:Timestamp.from(entry.releaseAt())));
    }
}
