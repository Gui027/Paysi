package com.paysi.checkout.charge.app;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.charge.port.ChargeCreationRepository;
import com.paysi.checkout.charge.port.ChargeCreationRepository.OrderContext;
import com.paysi.checkout.pricing.app.PriceMath;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.ledger.app.SaleLedgerService;
import com.paysi.payment.boleto.app.BoletoPaymentService;
import com.paysi.payment.card.app.CardPaymentService;
import com.paysi.payment.card.domain.CardPaymentCommand;
import com.paysi.payment.card.domain.SaleEvidenceCommand;
import com.paysi.payment.pix.app.PixPaymentService;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * BE-07.1: usa os motores já existentes (SplitEngine via PriceMath) para congelar a
 * divisão exata de uma cobrança de pedido avulso e despachar para o serviço do meio
 * de pagamento escolhido (BE-06.2/06.3/06.4).
 */
@Service
public class ChargeCreationService {
    private final ChargeCreationRepository repository;
    private final PlatformPlanReader plans;
    private final SaleLedgerService saleLedger;
    private final CardPaymentService cardPayments;
    private final BoletoPaymentService boletoPayments;
    private final PixPaymentService pixPayments;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ChargeCreationService(ChargeCreationRepository repository, PlatformPlanReader plans,
                                  SaleLedgerService saleLedger, CardPaymentService cardPayments,
                                  BoletoPaymentService boletoPayments, PixPaymentService pixPayments) {
        this(repository, plans, saleLedger, cardPayments, boletoPayments, pixPayments, Clock.systemUTC());
    }

    ChargeCreationService(ChargeCreationRepository repository, PlatformPlanReader plans,
                          SaleLedgerService saleLedger, CardPaymentService cardPayments,
                          BoletoPaymentService boletoPayments, PixPaymentService pixPayments, Clock clock) {
        this.repository = repository;
        this.plans = plans;
        this.saleLedger = saleLedger;
        this.cardPayments = cardPayments;
        this.boletoPayments = boletoPayments;
        this.pixPayments = pixPayments;
        this.clock = clock;
    }

    @Transactional
    public ChargeStartResult start(UUID orderId, StartChargeCommand command) {
        OrderContext ctx = repository.findOrderContext(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado"));
        OfferPaymentMethod method = OfferPaymentMethod.valueOf(ctx.method());

        UUID subscriptionId = ctx.recurring()
                ? repository.findSubscriptionForOrder(orderId).orElseGet(() ->
                        repository.insertOrReadSubscription(UUID.randomUUID(), orderId, ctx.offerId(),
                                command.cardToken(), clock.instant()))
                : null;

        UUID chargeId = repository.findChargeForOrder(orderId)
                .orElseGet(() -> create(orderId, subscriptionId, ctx, method));

        return switch (method) {
            case CARD -> {
                var evidence = new SaleEvidenceCommand(command.ip(), command.userAgent(), command.deviceKey(),
                        command.termsHash(), command.termsAcceptedAt());
                var result = cardPayments.start(chargeId,
                        new CardPaymentCommand(command.cardToken(), ctx.installments(), evidence));
                completeCard(chargeId, result);
                yield ChargeStartResult.card(chargeId, result);
            }
            case BOLETO -> {
                var result = boletoPayments.issue(chargeId, ctx.boletoDueDays());
                if (subscriptionId != null) {
                    repository.updateSubscriptionPayment(subscriptionId, "ACTIVE", nextCharge(ctx.cycle()),
                            chargeId, null);
                }
                yield ChargeStartResult.boleto(chargeId, result);
            }
            case PIX -> ChargeStartResult.pix(chargeId, pixPayments.start(chargeId));
        };
    }

    @Transactional
    public void completeCard(UUID chargeId, com.paysi.payment.card.domain.CardPaymentResult result) {
        Instant now = clock.instant();
        if ("approved".equals(result.status())) {
            repository.findRecurringCharge(chargeId).ifPresent(recurring ->
                    repository.updateSubscriptionPayment(recurring.subscriptionId(), "ACTIVE",
                            nextCharge(recurring.cycle()), chargeId, null));
            saleLedger.creditForCharge(chargeId, now);
        } else if ("declined".equals(result.status())) {
            repository.findRecurringCharge(chargeId).ifPresent(recurring ->
                    repository.updateSubscriptionPayment(recurring.subscriptionId(), "PAST_DUE", null,
                            chargeId, now.plus(Duration.ofDays(1))));
        }
    }

    @Transactional(readOnly = true)
    public ChargeCreationRepository.ChargeView view(UUID chargeId) {
        return repository.findChargeView(chargeId)
                .orElseThrow(() -> new NotFoundException("CHARGE_NOT_FOUND", "Cobrança não encontrada"));
    }

    @Transactional(readOnly = true)
    public ChargeCreationRepository.ChargeView viewOwned(UUID sellerId, UUID chargeId) {
        return repository.findChargeView(sellerId, chargeId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND", "Pagamento não encontrado"));
    }

    private UUID create(UUID orderId, UUID subscriptionId, OrderContext ctx, OfferPaymentMethod method) {
        Plan plan = Plan.valueOf(plans.currentPlan(ctx.sellerId()));
        Split split = PriceMath.split(ctx.paidCents(), method, ctx.installments(), plan, ctx.commissionBps());
        UUID chargeId = UUID.randomUUID();
        int feeBps = PriceMath.providerMethod(method, ctx.installments()).feeBps(plan);
        repository.insertCharge(chargeId, orderId, subscriptionId, ctx.paidCents(), plan.name(), feeBps,
                SplitEngine.PLATFORM_FIXED_FEE_CENTS,
                split.sellerFeeCents(), split.affiliateCents(), split.sellerCents(), "PENDING", clock.instant());
        return chargeId;
    }

    private Instant nextCharge(String cycle) {
        var now = clock.instant().atZone(java.time.ZoneOffset.UTC);
        return switch (cycle) {
            case "QUARTERLY" -> now.plusMonths(3).toInstant();
            case "SEMIANNUAL" -> now.plusMonths(6).toInstant();
            case "ANNUAL" -> now.plusYears(1).toInstant();
            default -> now.plusMonths(1).toInstant();
        };
    }
}
