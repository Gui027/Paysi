package com.paysi.ledger.jobs.app;

import com.paysi.ledger.jobs.port.IntegrityRepository;
import com.paysi.observability.alert.app.AlertService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * As oito verificações de integridade do documento 3 (checklist #12, RNF-043):
 * resultado não vazio em qualquer uma é incidente de severidade máxima, e cada
 * uma precisa alcançar o canal de observabilidade, não só o log. Uma view
 * violada dispara um alerta próprio — não um alerta agregado — para que quem
 * recebe saiba exatamente qual das oito verificações falhou sem abrir o banco.
 */
@Service
public class LedgerIntegrityMonitor {
    static final List<String> VIEWS = List.of(
            "v_check_unbalanced_transactions", "v_check_negative_user_buckets", "v_check_positive_debt",
            "v_check_system_sign_violation", "v_check_checkpoint_drift", "v_check_receivable_schedule",
            "v_check_refund_accumulator", "v_check_release_schedule");
    private static final Logger LOG = LoggerFactory.getLogger(LedgerIntegrityMonitor.class);
    private final IntegrityRepository repository;
    private final AlertService alerts;
    private final MeterRegistry meters;

    public LedgerIntegrityMonitor(IntegrityRepository repository, AlertService alerts, MeterRegistry meters) {
        this.repository = repository;
        this.alerts = alerts;
        this.meters = meters;
    }

    @Scheduled(cron = "${paysi.ledger.integrity-cron:0 0 * * * *}")
    public void scheduledRun() {
        inspect();
    }

    public List<IntegrityViolation> inspect() {
        var violations = new ArrayList<IntegrityViolation>();
        for (String view : VIEWS) {
            long rows = repository.violations(view);
            Counter.builder("paysi.ledger.integrity.checks").tag("view", view).register(meters).increment();
            if (rows > 0) {
                LOG.error("LEDGER_INTEGRITY_VIOLATION view={} rows={}", view, rows);
                alerts.raise("LEDGER_INTEGRITY_VIOLATION", "CRITICAL", Map.of("view", view, "rows", rows));
                violations.add(new IntegrityViolation(view, rows));
            }
        }
        return List.copyOf(violations);
    }
}
