package com.paysi.subscription.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BE-10.2: laços de varredura para cancelamento agendado, ciclo (teste/renovação) e retentativa de cobrança. */
@Component
public class SubscriptionJobs {
    private static final int BATCH_SIZE = 200;

    private final SubscriptionCycleProcessor cycles;
    private final SubscriptionRetryProcessor retries;

    public SubscriptionJobs(SubscriptionCycleProcessor cycles, SubscriptionRetryProcessor retries) {
        this.cycles = cycles;
        this.retries = retries;
    }

    @Scheduled(cron = "${paysi.subscription.cancellation-cron:0 */5 * * * *}")
    public void applyDueCancellations() {
        for (int processed = 0; processed < BATCH_SIZE && cycles.processNextCancellation(); processed++) { }
    }

    @Scheduled(cron = "${paysi.subscription.cycle-cron:0 */10 * * * *}")
    public void runDueCycles() {
        for (int processed = 0; processed < BATCH_SIZE && cycles.processNextCycle(); processed++) { }
    }

    @Scheduled(cron = "${paysi.subscription.retry-cron:0 */15 * * * *}")
    public void runDueRetries() {
        for (int processed = 0; processed < BATCH_SIZE && retries.processNext(); processed++) { }
    }
}
