package com.paysi.billing.plan.app;

import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.port.PlanRepository;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import com.paysi.payment.provider.*;
import com.paysi.payment.split.Plan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * BE-14.2: fecha o ciclo mensal do plano comercial. RF-102: a mensalidade do Escala sai primeiro do
 * saldo disponível; só tenta o cartão cadastrado se o saldo não cobrir. RF-102 também rebaixa para
 * TRANSACIONAL depois de 10 dias em atraso.
 */
@Service
public class PlanBillingProcessor {
    /** SYS_PLATFORM_REVENUE (V029): contrapartida da mensalidade da plataforma. */
    private static final java.util.UUID SYS_PLATFORM_REVENUE =
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    /** SYS_CLEARING (V029): origem do dinheiro que entra do provedor. */
    private static final java.util.UUID SYS_CLEARING =
            java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private final PlanRepository plans;
    private final LedgerService ledger;
    private final PaymentProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PlanBillingProcessor(PlanRepository plans, LedgerService ledger, PaymentProvider provider) {
        this(plans, ledger, provider, Clock.systemUTC());
    }

    PlanBillingProcessor(PlanRepository plans, LedgerService ledger, PaymentProvider provider, Clock clock) {
        this.plans = plans;
        this.ledger = ledger;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextRollover() {
        Instant now = clock.instant();
        var due = plans.claimDueRollover(now);
        if (due.isEmpty()) return false;
        var subscription = due.get();

        Plan plan = subscription.hasPendingChange() ? subscription.pendingPlan() : subscription.plan();
        long price = subscription.hasPendingChange() ? subscription.pendingPriceCents() : subscription.priceCents();
        Instant periodStart = subscription.currentPeriodEnd();
        Instant periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();

        if (plan != Plan.ESCALA || price <= 0) {
            plans.applyRollover(subscription.accountId(), plan.name(), price, periodStart, periodEnd,
                    "ACTIVE", null);
            return true;
        }

        boolean paid = chargeAvailableBalance(subscription.accountId(), price, periodStart)
                || chargeCard(subscription, price, periodStart);
        plans.applyRollover(subscription.accountId(), plan.name(), price, periodStart, periodEnd,
                paid ? "ACTIVE" : "PAST_DUE", paid ? null : now);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextDowngrade() {
        Instant now = clock.instant();
        var due = plans.claimDueDowngrade(now);
        if (due.isEmpty()) return false;
        Instant periodStart = now;
        Instant periodEnd = ZonedDateTime.ofInstant(periodStart, ZoneOffset.UTC).plusMonths(1).toInstant();
        plans.applyDowngrade(due.get(), periodStart, periodEnd);
        return true;
    }

    private boolean chargeAvailableBalance(java.util.UUID accountId, long amountCents, Instant periodStart) {
        try {
            var entries = java.util.List.of(
                    new LedgerEntry(accountId, Bucket.AVAILABLE, Direction.DEBIT, amountCents, Origin.FEE, null),
                    new LedgerEntry(SYS_PLATFORM_REVENUE, Bucket.SYSTEM, Direction.CREDIT, amountCents, Origin.FEE,
                            null));
            ledger.write(new LedgerCommand(TransactionType.PLATFORM_FEE,
                    new LedgerReference(ReferenceType.PLATFORM_SUB, accountId + ":" + periodStart),
                    "Mensalidade do plano Escala", entries));
            return true;
        } catch (ValidationException insufficientBalance) {
            return false;
        }
    }

    private boolean chargeCard(PlatformSubscription subscription, long amountCents, Instant periodStart) {
        if (subscription.providerToken() == null || subscription.providerToken().isBlank()) return false;
        var account = plans.billingInfo(subscription.accountId());
        var buyer = new ProviderBuyer(account.name(), account.email(), account.personType(), account.taxId());
        var result = provider.charge(new ProviderPaymentRequest(subscription.accountId(), amountCents,
                ProviderPaymentMethod.CARD, 1, subscription.providerToken(), buyer,
                new ProviderSplit(0, 0, amountCents)));
        if (result.status() != ProviderChargeStatus.APPROVED) return false;
        // Dinheiro veio direto do cartão para a plataforma: não mexe no saldo do vendedor,
        // só registra a receita (mesma convenção do SYS_CLEARING usada nas vendas).
        var entries = java.util.List.of(
                new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.DEBIT, amountCents, Origin.FEE, null),
                new LedgerEntry(SYS_PLATFORM_REVENUE, Bucket.SYSTEM, Direction.CREDIT, amountCents, Origin.FEE,
                        null));
        ledger.write(new LedgerCommand(TransactionType.PLATFORM_FEE,
                new LedgerReference(ReferenceType.PLATFORM_SUB, subscription.accountId() + ":" + periodStart),
                "Mensalidade do plano Escala (cartão)", entries));
        return true;
    }
}
