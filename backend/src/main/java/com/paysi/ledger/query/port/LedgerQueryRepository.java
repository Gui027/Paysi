package com.paysi.ledger.query.port;

import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.query.app.LedgerItem;
import java.util.*;

public interface LedgerQueryRepository {
    Map<Bucket,Long> balances(UUID accountId);
    List<LedgerItem> entries(UUID accountId, Long beforeEntryId, int limit);
    void consolidate(UUID accountId);
}
