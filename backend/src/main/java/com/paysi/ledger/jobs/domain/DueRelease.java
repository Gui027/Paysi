package com.paysi.ledger.jobs.domain;

import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Origin;

import java.time.Instant;
import java.util.UUID;

public record DueRelease(long entryId, UUID accountId, Bucket bucket, long amountCents, Origin origin,
                         Instant sourceCreatedAt, Instant pendingReleaseAt, String payoutDelay) { }
