package com.paysi.risk.app;

import com.paysi.risk.port.RiskRepository;
import com.paysi.webhook.app.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * BE-12.2 / RF-076, RF-077, RF-106: recalcula os índices de contestação e reembolso do vendedor e
 * da plataforma e aplica os limiares objetivos. RF-078 exige notificar com motivo específico e
 * memória de cálculo ANTES de qualquer bloqueio — por isso o evento sai antes da mudança de status
 * da conta, nunca depois, e cada ação grava sua razão em {@code risk_events} mesmo quando é só alerta.
 */
@Service
public class RiskService {
    /** Sentinela usada para eventos de escopo plataforma, que não têm uma conta dona (RF-106). */
    private static final UUID PLATFORM_SCOPE = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final RiskRepository repository;
    private final OutboxService outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RiskService(RiskRepository repository, OutboxService outbox) {
        this(repository, outbox, Clock.systemUTC());
    }

    RiskService(RiskRepository repository, OutboxService outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public SellerRiskSnapshot recalculateSeller(UUID sellerId) {
        var metrics = repository.sellerMetrics(sellerId);
        int chargebackBps = rateBps(metrics.disputedCents(), metrics.volumeCents());
        int refundBps = rateBps(metrics.refundedCents(), metrics.volumeCents());
        Instant now = clock.instant();

        repository.upsertAccountRisk(sellerId, metrics.volumeCents(), chargebackBps, refundBps, now);

        String disputeAction = applyThreshold(sellerId, "dispute_rate", chargebackBps, now,
                new Step(RiskThresholds.SELLER_DISPUTE_ALERT_BPS, "ALERT"),
                new Step(RiskThresholds.SELLER_DISPUTE_SUSPEND_BPS, "SUSPEND"),
                new Step(RiskThresholds.SELLER_DISPUTE_LIMIT_BPS, "LIMIT"));
        String refundAction = applyThreshold(sellerId, "refund_rate", refundBps, now,
                new Step(RiskThresholds.SELLER_REFUND_ALERT_BPS, "ALERT"),
                new Step(RiskThresholds.SELLER_REFUND_REVIEW_BPS, "LIMIT"),
                new Step(RiskThresholds.SELLER_REFUND_SUSPEND_BPS, "SUSPEND"));

        return new SellerRiskSnapshot(sellerId, chargebackBps, refundBps, disputeAction, refundAction);
    }

    @Transactional
    public PlatformRiskSnapshot recalculatePlatform() {
        var metrics = repository.platformMetrics();
        int chargebackBps = rateBps(metrics.disputedCents(), metrics.volumeCents());
        int refundBps = rateBps(metrics.refundedCents(), metrics.volumeCents());
        Instant now = clock.instant();

        repository.upsertPlatformRiskIndex(LocalDate.now(clock), chargebackBps, refundBps, metrics.volumeCents(),
                now);

        boolean frozen = chargebackBps >= RiskThresholds.PLATFORM_DISPUTE_FREEZE_BPS;
        boolean alerted = frozen || chargebackBps >= RiskThresholds.PLATFORM_DISPUTE_ALERT_BPS;
        if (frozen) {
            outbox.append(PLATFORM_SCOPE, "risk.platform_freeze_new_accounts",
                    new PlatformRiskEvent(chargebackBps, RiskThresholds.PLATFORM_DISPUTE_FREEZE_BPS,
                            "Índice de contestação agregado da plataforma atingiu " + chargebackBps
                                    + " bps (limiar de congelamento: " + RiskThresholds.PLATFORM_DISPUTE_FREEZE_BPS
                                    + " bps) — aprovação de contas novas congelada"));
        } else if (alerted) {
            outbox.append(PLATFORM_SCOPE, "risk.platform_alert",
                    new PlatformRiskEvent(chargebackBps, RiskThresholds.PLATFORM_DISPUTE_ALERT_BPS,
                            "Índice de contestação agregado da plataforma atingiu " + chargebackBps
                                    + " bps (limiar de alerta: " + RiskThresholds.PLATFORM_DISPUTE_ALERT_BPS
                                    + " bps)"));
        }
        return new PlatformRiskSnapshot(chargebackBps, refundBps, alerted, frozen);
    }

    /** Aplica só o degrau mais severo atingido — não empilha ALERT + SUSPEND para o mesmo evento. */
    private String applyThreshold(UUID accountId, String metric, int valueBps, Instant now, Step... ascendingSteps) {
        Step hit = null;
        for (Step step : ascendingSteps) {
            if (valueBps >= step.thresholdBps()) hit = step;
        }
        if (hit == null) return null;

        boolean blocking = !"ALERT".equals(hit.kind());
        String reason = "%s atingiu %d bps (limiar %s: %d bps)".formatted(metric, valueBps, hit.kind(),
                hit.thresholdBps());
        Instant notifiedAt = now;

        // RF-078: notifica ANTES de qualquer bloqueio — o evento sai antes da mudança de status.
        outbox.append(accountId, blocking ? "risk.account_restricted" : "risk.alert",
                new RiskEvent(metric, hit.kind(), valueBps, hit.thresholdBps(), reason));
        repository.insertRiskEvent(UUID.randomUUID(), accountId, hit.kind(), metric, valueBps, hit.thresholdBps(),
                reason, notifiedAt, now);
        if (blocking) {
            repository.updateAccountStatus(accountId, "SUSPEND".equals(hit.kind()) ? "SUSPENDED" : "LIMITED");
        }
        return hit.kind();
    }

    private static int rateBps(long numeratorCents, long denominatorCents) {
        if (denominatorCents <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.multiplyExact(numeratorCents, 10_000L) / denominatorCents);
    }

    private record Step(int thresholdBps, String kind) {
    }

    private record RiskEvent(String metric, String kind, int valueBps, int thresholdBps, String reason) {
    }

    private record PlatformRiskEvent(int valueBps, int thresholdBps, String reason) {
    }
}
