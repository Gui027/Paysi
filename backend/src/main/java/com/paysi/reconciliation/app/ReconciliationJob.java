package com.paysi.reconciliation.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** BE-14.4: conciliação diária — compara o dia anterior completo, já fechado no extrato. */
@Component
public class ReconciliationJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationJob.class);

    private final ReconciliationService service;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ReconciliationJob(ReconciliationService service) {
        this(service, Clock.systemUTC());
    }

    ReconciliationJob(ReconciliationService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(cron = "${paysi.reconciliation.daily-cron:0 0 4 * * *}")
    public void runDaily() {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneOffset.UTC)).minusDays(1);
        var report = service.reconcile(yesterday);
        long diverged = report.entries().stream().filter(com.paysi.reconciliation.domain.ReconciliationEntry::diverged).count();
        if (diverged > 0) {
            LOG.warn("RECONCILIATION_DIVERGENCES date={} count={}", yesterday, diverged);
        }
    }
}
