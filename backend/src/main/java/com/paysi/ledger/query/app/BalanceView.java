package com.paysi.ledger.query.app;

import com.paysi.ledger.domain.Bucket;
import java.time.Instant;
import java.util.Map;

public record BalanceView(long guarantee, long pending, long reserve, long available, long debt, Instant asOf) {
    static BalanceView from(Map<Bucket,Long> values,Instant asOf){return new BalanceView(value(values,Bucket.GUARANTEE),value(values,Bucket.PENDING),value(values,Bucket.RESERVE),value(values,Bucket.AVAILABLE),value(values,Bucket.DEBT),asOf);}
    private static long value(Map<Bucket,Long> values,Bucket bucket){return values.getOrDefault(bucket,0L);}
}
