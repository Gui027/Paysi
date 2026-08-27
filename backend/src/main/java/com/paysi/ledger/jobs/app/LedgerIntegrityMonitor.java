package com.paysi.ledger.jobs.app;

import com.paysi.ledger.jobs.port.IntegrityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LedgerIntegrityMonitor {
    static final List<String> VIEWS = List.of(
            "v_check_unbalanced_transactions", "v_check_negative_user_buckets", "v_check_positive_debt",
            "v_check_system_sign_violation", "v_check_checkpoint_drift", "v_check_receivable_schedule",
            "v_check_refund_accumulator", "v_check_release_schedule");
    private static final Logger LOG = LoggerFactory.getLogger(LedgerIntegrityMonitor.class);
    private final IntegrityRepository repository;

    public LedgerIntegrityMonitor(IntegrityRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${paysi.ledger.integrity-cron:0 0 * * * *}")
    public void scheduledRun() {
        inspect();
    }

    public List<IntegrityViolation> inspect() {
        var violations = new ArrayList<IntegrityViolation>();
        for (String view : VIEWS) {
            long rows = repository.violations(view);
            if (rows > 0) {
                LOG.error("LEDGER_INTEGRITY_VIOLATION view={} rows={}", view, rows);
                violations.add(new IntegrityViolation(view, rows));
            }
        }
        return List.copyOf(violations);
    }
}
