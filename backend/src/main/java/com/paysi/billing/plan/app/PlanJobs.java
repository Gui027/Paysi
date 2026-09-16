package com.paysi.billing.plan.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** BE-14.2: fecha ciclos de plano vencidos e aplica rebaixamento automático após 10 dias em atraso. */
@Component
public class PlanJobs {
    private static final int BATCH_SIZE = 200;

    private final PlanBillingProcessor processor;

    public PlanJobs(PlanBillingProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(cron = "${paysi.billing.plan-rollover-cron:0 0 3 * * *}")
    public void runDueRollovers() {
        for (int processed = 0; processed < BATCH_SIZE && processor.processNextRollover(); processed++) { }
    }

    @Scheduled(cron = "${paysi.billing.plan-downgrade-cron:0 30 3 * * *}")
    public void runDueDowngrades() {
        for (int processed = 0; processed < BATCH_SIZE && processor.processNextDowngrade(); processed++) { }
    }
}
