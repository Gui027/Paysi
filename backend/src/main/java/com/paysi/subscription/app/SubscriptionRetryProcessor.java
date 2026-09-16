package com.paysi.subscription.app;

import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.provider.*;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;
import com.paysi.subscription.port.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** BE-10.2: reprocessa cobranças de ciclo recusadas seguindo a régua de retentativa D+1/3/7/14. */
@Service
public class SubscriptionRetryProcessor {
    private final SubscriptionRepository subscriptions;
    private final PlatformPlanReader plans;
    private final PaymentProvider provider;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionRetryProcessor(SubscriptionRepository subscriptions, PlatformPlanReader plans,
                                       PaymentProvider provider) {
        this(subscriptions, plans, provider, Clock.systemUTC());
    }

    SubscriptionRetryProcessor(SubscriptionRepository subscriptions, PlatformPlanReader plans,
                                PaymentProvider provider, Clock clock) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.provider = provider;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processNext() {
        Instant now = clock.instant();
        var due = subscriptions.claimDueRetry(now);
        if (due.isEmpty()) return false;
        var retry = due.get();

        if (retry.providerToken() == null || retry.providerToken().isBlank()) {
            giveUp(retry.chargeId(), retry.subscriptionId());
            return true;
        }

        Plan plan = Plan.valueOf(plans.currentPlan(retry.sellerId()));
        Split split = SplitEngine.split(retry.amountCents(), PaymentMethod.CARD_1, plan, 0);
        var buyer = new ProviderBuyer(retry.buyerName(), retry.buyerEmail(), retry.personType(), retry.taxId());
        var result = provider.charge(new ProviderPaymentRequest(retry.orderId(), retry.amountCents(),
                ProviderPaymentMethod.CARD, 1, retry.providerToken(), buyer,
                new ProviderSplit(split.sellerCents(), split.affiliateCents(), split.sellerFeeCents())));

        boolean approved = result.status() == ProviderChargeStatus.APPROVED;
        if (approved) {
            subscriptions.saveChargeResult(retry.chargeId(), "PAID", result.providerChargeId(),
                    result.providerFeeCents(), now, now, null);
            subscriptions.markOrderStatus(retry.orderId(), "PAID", now);
            subscriptions.updateSubscriptionCycle(retry.subscriptionId(), "ACTIVE",
                    SubscriptionService.nextCharge(now, retry.cycle()));
            return true;
        }

        int newAttemptCount = retry.attemptCount() + 1;
        var delay = DunningSchedule.nextDelay(newAttemptCount);
        if (delay == null) {
            giveUp(retry.chargeId(), retry.subscriptionId());
        } else {
            subscriptions.scheduleRetry(retry.chargeId(), newAttemptCount, now.plus(delay));
        }
        return true;
    }

    private void giveUp(java.util.UUID chargeId, java.util.UUID subscriptionId) {
        subscriptions.exhaustRetry(chargeId, subscriptionId);
    }
}
