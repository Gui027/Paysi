package com.paysi.subscription.app;

import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.provider.*;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;
import com.paysi.subscription.port.SubscriptionRepository;
import com.paysi.subscription.port.SubscriptionRepository.DueCycle;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * BE-10.2: fim de teste grátis vira primeira cobrança e renovação de ciclo ativo.
 * Cartão cobra e confirma na hora (como BE-10.1); boleto emite o próximo ciclo e aguarda a
 * confirmação assíncrona já tratada pelo inbox do provedor (fora do escopo deste job).
 */
@Service
public class SubscriptionCycleProcessor {
    private final SubscriptionRepository subscriptions;
    private final PlatformPlanReader plans;
    private final PaymentProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionCycleProcessor(SubscriptionRepository subscriptions, PlatformPlanReader plans,
                                       PaymentProvider provider) {
        this(subscriptions, plans, provider, Clock.systemUTC());
    }

    SubscriptionCycleProcessor(SubscriptionRepository subscriptions, PlatformPlanReader plans,
                                PaymentProvider provider, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextCancellation() {
        Instant now = clock.instant();
        var due = subscriptions.claimDueCancellation(now);
        due.ifPresent(subscriptionId -> subscriptions.applyCancellation(subscriptionId));
        return due.isPresent();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNextCycle() {
        Instant now = clock.instant();
        var due = subscriptions.claimDueCycle(now);
        if (due.isEmpty()) return false;
        var cycle = due.get();

        if ("BOLETO".equals(cycle.orderMethod())) {
            issueBoleto(cycle, now);
        } else if (blank(cycle.providerToken())) {
            // Teste sem cartão terminou sem meio de pagamento anexado: fica pendente até o
            // comprador atualizar o cartão (fluxo de atualização é escopo futuro).
            subscriptions.updateSubscriptionStatus(cycle.subscriptionId(), "PAST_DUE");
        } else {
            chargeCard(cycle, now);
        }
        return true;
    }

    private void chargeCard(DueCycle cycle, Instant now) {
        Plan plan = Plan.valueOf(plans.currentPlan(cycle.sellerId()));
        Split split = SplitEngine.split(cycle.priceCents(), PaymentMethod.CARD_1, plan, 0);
        UUID chargeId = UUID.randomUUID();
        subscriptions.insertCharge(chargeId, cycle.orderId(), cycle.subscriptionId(), cycle.nextCycleNumber(),
                cycle.priceCents(), plan.name(), PaymentMethod.CARD_1.feeBps(plan), 200, split.sellerFeeCents(),
                split.affiliateCents(), split.sellerCents(), "PENDING", now);

        var buyer = new ProviderBuyer(cycle.buyerName(), cycle.buyerEmail(), cycle.personType(), cycle.taxId());
        var result = provider.charge(new ProviderPaymentRequest(cycle.orderId(), cycle.priceCents(),
                ProviderPaymentMethod.CARD, 1, cycle.providerToken(), buyer,
                new ProviderSplit(split.sellerCents(), split.affiliateCents(), split.sellerFeeCents())));

        boolean approved = result.status() == ProviderChargeStatus.APPROVED;
        subscriptions.saveChargeResult(chargeId, approved ? "PAID" : "FAILED", result.providerChargeId(),
                result.providerFeeCents(), approved ? now : null, approved ? now : null,
                approved ? null : now.plus(DunningSchedule.FIRST_RETRY_DELAY));
        subscriptions.markOrderStatus(cycle.orderId(), approved ? "PAID" : "FAILED", approved ? now : null);
        subscriptions.updateSubscriptionCycle(cycle.subscriptionId(),
                approved ? "ACTIVE" : "PAST_DUE",
                approved ? SubscriptionService.nextCharge(now, cycle.cycle()) : null);
    }

    private void issueBoleto(DueCycle cycle, Instant now) {
        Plan plan = Plan.valueOf(plans.currentPlan(cycle.sellerId()));
        Split split = SplitEngine.split(cycle.priceCents(), PaymentMethod.BOLETO, plan, 0);
        UUID chargeId = UUID.randomUUID();
        subscriptions.insertCharge(chargeId, cycle.orderId(), cycle.subscriptionId(), cycle.nextCycleNumber(),
                cycle.priceCents(), plan.name(), PaymentMethod.BOLETO.feeBps(plan), 200, split.sellerFeeCents(),
                split.affiliateCents(), split.sellerCents(), "PENDING", now);

        var buyer = new ProviderBuyer(cycle.buyerName(), cycle.buyerEmail(), cycle.personType(), cycle.taxId());
        var result = provider.charge(new ProviderPaymentRequest(cycle.orderId(), cycle.priceCents(),
                ProviderPaymentMethod.BOLETO, 1, null, buyer,
                new ProviderSplit(split.sellerCents(), split.affiliateCents(), split.sellerFeeCents()),
                cycle.boletoDueDays()));

        // Boleto não confirma na hora: fica PENDING até o inbox do provedor liquidar ou expirar
        // (BoletoExpirationJob/BoletoPaymentService, já existentes, cuidam do resto do ciclo de vida).
        subscriptions.saveChargeResult(chargeId, "PENDING", result.providerChargeId(), result.providerFeeCents(),
                null, null, null);
        subscriptions.updateSubscriptionCycle(cycle.subscriptionId(), "ACTIVE",
                SubscriptionService.nextCharge(now, cycle.cycle()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
