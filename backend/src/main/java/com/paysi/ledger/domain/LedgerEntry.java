package com.paysi.ledger.domain;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(UUID accountId, Bucket bucket, Direction direction, long amountCents,
                          Origin origin, Instant releaseAt) {
    public LedgerEntry {
        if (accountId == null || bucket == null || direction == null || origin == null) throw new IllegalArgumentException("Ledger entry fields are required");
        if (amountCents <= 0) throw new IllegalArgumentException("Ledger amount must be positive");
        if (releaseAt != null && (direction != Direction.CREDIT || bucket == Bucket.SYSTEM)) throw new IllegalArgumentException("Only user credits can be scheduled");
    }
}
