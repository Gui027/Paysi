package com.paysi.payout.adapter;

import com.paysi.payout.domain.BankAccount;
import com.paysi.payout.port.PayoutRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPayoutRepository implements PayoutRepository {
    private final JdbcTemplate jdbc;
    public JdbcPayoutRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<AccountHolder> accountHolder(UUID id) { return jdbc.query("select tax_id,full_name from accounts where id=? and status='ACTIVE'",(rs,row)->new AccountHolder(rs.getString(1),rs.getString(2)),id).stream().findFirst(); }
    @Override public void insertBank(BankAccount b,byte[] number,byte[] pix){jdbc.update("insert into bank_accounts(id,account_id,bank_code,branch,number_enc,number_last4,holder_tax_id,pix_key_enc,verified_at,holder_type,holder_name,account_type,pix_key_type) values (?,?,?,?,?,?,?,?,?,?,?,?,?)",b.id(),b.accountId(),b.bankCode(),b.branch(),number,b.numberLast4(),b.holderTaxId(),pix,Timestamp.from(b.verifiedAt()),b.holderType(),b.holderName(),b.accountType(),b.pixKeyType());}
    @Override public Optional<BankAccount> lockBank(UUID id){return jdbc.query("select id,account_id,bank_code,branch,number_last4,holder_tax_id,holder_type,holder_name,account_type,pix_key_type,verified_at,archived_at from bank_accounts where id=? for update",(rs,row)->new BankAccount(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),instant(rs.getTimestamp(11)),instant(rs.getTimestamp(12))),id).stream().findFirst();}
    @Override public void archiveBank(UUID account,UUID bank,Instant now){jdbc.update("update bank_accounts set archived_at=? where id=? and account_id=? and archived_at is null",Timestamp.from(now),bank,account);}
    @Override public Optional<PayoutState> findPayout(UUID account,String key){return jdbc.query("select id,amount_cents,bank_account_id,status,receipt_url from payouts where account_id=? and idempotency_key=?",(rs,row)->new PayoutState(rs.getObject(1,UUID.class),rs.getLong(2),rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5)),account,key).stream().findFirst();}
    @Override public long lockAndReadDebt(UUID account){jdbc.query("select pg_advisory_xact_lock(4210,hashtext(?))",rs->null,account.toString());Long value=jdbc.queryForObject("select coalesce(sum(case when direction='CREDIT' then amount_cents else -amount_cents end),0) from ledger_entries where account_id=? and bucket='DEBT'",Long.class,account);return value==null?0:value;}
    @Override public boolean insertPayout(UUID id,UUID account,long amount,UUID bank,String key){return jdbc.update("insert into payouts(id,account_id,amount_cents,bank_account_id,idempotency_key) values (?,?,?,?,?) on conflict(account_id,idempotency_key) do nothing",id,account,amount,bank,key)==1;}
    @Override public void markSent(UUID id,String providerId,String receiptUrl){jdbc.update("update payouts set status='SENT',provider_transfer_id=?,receipt_url=? where id=?",providerId,receiptUrl,id);}
    private static Instant instant(Timestamp value){return value==null?null:value.toInstant();}
}
