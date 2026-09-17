package com.paysi.checkout.charge.app;

import com.paysi.affiliate.app.CommissionService;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.charge.port.ChargeCreationRepository;
import com.paysi.checkout.charge.port.ChargeCreationRepository.OrderContext;
import com.paysi.checkout.pricing.app.PriceMath;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.boleto.app.BoletoPaymentService;
import com.paysi.payment.card.app.CardPaymentService;
import com.paysi.payment.card.domain.CardPaymentCommand;
import com.paysi.payment.card.domain.SaleEvidenceCommand;
import com.paysi.payment.pix.app.PixPaymentService;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final CommissionService commissions;
    private final CardPaymentService cardPayments;
    private final BoletoPaymentService boletoPayments;
    private final PixPaymentService pixPayments;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ChargeCreationService(ChargeCreationRepository repository, PlatformPlanReader plans,
                                  CommissionService commissions, CardPaymentService cardPayments,
                                  BoletoPaymentService boletoPayments, PixPaymentService pixPayments) {
        this(repository, plans, commissions, cardPayments, boletoPayments, pixPayments, Clock.systemUTC());
    }

    ChargeCreationService(ChargeCreationRepository repository, PlatformPlanReader plans,
                          CommissionService commissions, CardPaymentService cardPayments,
                          BoletoPaymentService boletoPayments, PixPaymentService pixPayments, Clock clock) {
        this.repository = repository;
        this.plans = plans;
        this.commissions = commissions;
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

        UUID chargeId = repository.findChargeForOrder(orderId).orElseGet(() -> create(orderId, ctx, method));

        return switch (method) {
            case CARD -> {
                var evidence = new SaleEvidenceCommand(command.ip(), command.userAgent(), command.deviceKey(),
                        command.termsHash(), command.termsAcceptedAt());
                var result = cardPayments.start(chargeId,
                        new CardPaymentCommand(command.cardToken(), ctx.installments(), evidence));
                if ("approved".equals(result.status()) && ctx.affiliateId() != null) {
                    liquidateAffiliateCommission(chargeId, ctx);
                }
                yield ChargeStartResult.card(chargeId, result);
            }
            case BOLETO -> ChargeStartResult.boleto(chargeId, boletoPayments.issue(chargeId, ctx.boletoDueDays()));
            case PIX -> ChargeStartResult.pix(chargeId, pixPayments.start(chargeId));
        };
    }

    private UUID create(UUID orderId, OrderContext ctx, OfferPaymentMethod method) {
        Plan plan = Plan.valueOf(plans.currentPlan(ctx.sellerId()));
        Split split = PriceMath.split(ctx.paidCents(), method, ctx.installments(), plan, ctx.commissionBps());
        UUID chargeId = UUID.randomUUID();
        int feeBps = PriceMath.providerMethod(method, ctx.installments()).feeBps(plan);
        repository.insertCharge(chargeId, orderId, ctx.paidCents(), plan.name(), feeBps, 200,
                split.sellerFeeCents(), split.affiliateCents(), split.sellerCents(), "PENDING", clock.instant());
        return chargeId;
    }

    /** Recalcula a mesma divisão só para saber quanto do afiliado liquidar — a cobrança já está congelada. */
    private void liquidateAffiliateCommission(UUID chargeId, OrderContext ctx) {
        Plan plan = Plan.valueOf(plans.currentPlan(ctx.sellerId()));
        OfferPaymentMethod method = OfferPaymentMethod.valueOf(ctx.method());
        Split split = PriceMath.split(ctx.paidCents(), method, ctx.installments(), plan, ctx.commissionBps());
        if (split.affiliateCents() <= 0) return;
        Instant now = clock.instant();
        commissions.liquidate(ctx.affiliateId(), split.affiliateCents(), chargeId, now, ctx.guaranteeDays());
    }
}
