package com.paysi.ledger.query.adapter;

import com.paysi.ledger.domain.*;
import com.paysi.ledger.query.app.LedgerItem;
import com.paysi.ledger.query.port.LedgerQueryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class JdbcLedgerQueryRepository implements LedgerQueryRepository {
    private final JdbcTemplate jdbc;public JdbcLedgerQueryRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override public Map<Bucket,Long> balances(UUID accountId){
        var result=new EnumMap<Bucket,Long>(Bucket.class);
        String sql="""
          with buckets(bucket) as (values ('GUARANTEE'),('PENDING'),('RESERVE'),('AVAILABLE'),('DEBT'))
          select b.bucket, coalesce(c.balance_cents,0)+coalesce(sum(case when e.direction='CREDIT' then e.amount_cents else -e.amount_cents end),0)
          from buckets b left join ledger_checkpoints c on c.account_id=? and c.bucket=b.bucket
          left join ledger_entries e on e.account_id=? and e.bucket=b.bucket and e.id>coalesce(c.up_to_entry_id,0)
          group by b.bucket,c.balance_cents
          """;
        jdbc.query(sql,rs->{while(rs.next())result.put(Bucket.valueOf(rs.getString(1)),rs.getLong(2));},accountId,accountId);
        return result;
    }

    @Override public List<LedgerItem> entries(UUID accountId,Long before,int limit){
        String cursor=before==null?"":" and e.id < ?";
        String sql="select e.id,e.bucket,e.direction,e.amount_cents,e.origin,t.description,t.reference_type,t.reference_id,e.release_at,e.created_at from ledger_entries e join ledger_transactions t on t.id=e.transaction_id where e.account_id=?"+cursor+" order by e.id desc limit ?";
        Object[] args=before==null?new Object[]{accountId,limit}:new Object[]{accountId,before,limit};
        return jdbc.query(sql,(rs,row)->new LedgerItem(rs.getLong(1),Bucket.valueOf(rs.getString(2)),Direction.valueOf(rs.getString(3)),rs.getLong(4),Origin.valueOf(rs.getString(5)),rs.getString(6),rs.getString(7)+":"+rs.getString(8),rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getTimestamp(10).toInstant()),args);
    }
    @Override public void consolidate(UUID accountId){jdbc.query("select ledger_consolidate_account(?)",rs->null,accountId);}
}
