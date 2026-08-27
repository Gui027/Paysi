package com.paysi.ledger.jobs.app;

import com.paysi.ledger.domain.Bucket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LedgerReleaseJobs {
    private static final int BATCH_SIZE = 500;
    private final LedgerReleaseProcessor processor;

    public LedgerReleaseJobs(LedgerReleaseProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(cron = "${paysi.ledger.release-cron:0 * * * * *}")
    public void run() {
        for (Bucket bucket : new Bucket[]{Bucket.GUARANTEE, Bucket.PENDING, Bucket.RESERVE}) {
            for (int processed = 0; processed < BATCH_SIZE && processor.processNext(bucket); processed++) { }
        }
    }
}
