package com.paysi.ledger.query.app;

import com.paysi.ledger.domain.*;
import java.time.Instant;

public record LedgerItem(long entryId, Bucket bucket, Direction direction, long amountCents, Origin origin,
                         String reason, String reference, Instant availableAt, Instant createdAt) { }
