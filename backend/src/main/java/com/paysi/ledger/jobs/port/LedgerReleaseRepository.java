package com.paysi.ledger.jobs.port;

import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.jobs.domain.DueRelease;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LedgerReleaseRepository {
    Optional<DueRelease> claimNext(Bucket bucket, Instant now);
    long lockAndReadDebt(UUID accountId);
    boolean markReleased(long entryId, UUID transactionId, Instant releasedAt);
}
