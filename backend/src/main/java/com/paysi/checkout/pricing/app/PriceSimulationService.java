package com.paysi.checkout.pricing.app;

import com.paysi.catalog.coupon.app.CouponDiscount;
import com.paysi.catalog.coupon.app.CouponRedemptionService;
import com.paysi.catalog.offer.app.OfferAvailability;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.checkout.pricing.port.OfferSellerLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Fonte única do preço do checkout. A simulação exibida antes da compra e a criação do
 * pedido passam pelo mesmo cálculo, então não existe caminho em que a tela mostre um
 * número e o pedido grave outro.
 *
 * <p>Nada aqui é persistido: a simulação é um cálculo, e a memória de divisão só é
 * congelada quando a cobrança é criada (BE-07.1).
 */
@Service
public class PriceSimulationService {
    /** Mínimo que o adquirente aceita. Distinto do piso comercial de R$ 20,00 da oferta. */
    private static final long TECHNICAL_FLOOR_CENTS = 500;

    private final OfferRepository offers;
    private final CouponRedemptionService coupons;
    private final OfferSellerLookup sellers;
    private final PlatformPlanReader plans;
    private final Clock clock;

    @Autowired
    public PriceSimulationService(OfferRepository offers, CouponRedemptionService coupons,
            OfferSellerLookup sellers, PlatformPlanReader plans) {
        this(offers, coupons, sellers, plans, Clock.systemUTC());
    }

    PriceSimulationService(OfferRepository offers, CouponRedemptionService coupons,
            OfferSellerLookup sellers, PlatformPlanReader plans, Clock clock) {
        this.offers = offers;
        this.coupons = coupons;
        this.sellers = sellers;
        this.plans = plans;
        this.clock = clock;
    }

    /** Simulação pública: calcula e não consome unidade de cupom. */
    @Transactional(readOnly = true)
    public PriceQuote simulate(String slug, OfferPaymentMethod method, int installments, String couponCode) {
        Offer offer = offers.findPublishedBySlug(slug).orElseThrow(PriceSimulationService::offerNotFound);
        CouponDiscount discount = coupons.quote(offer.id(), couponCode, offer.priceCents());
        return price(offer, method, installments, discount, 0);
    }

    /**
     * Cálculo da criação do pedido: exatamente a mesma aritmética da simulação, com a
     * comissão do afiliado já resolvida. Continua sem escrever nada — consumir a
     * unidade do cupom é decisão do serviço de pedido, e só depois que o pedido existe.
     */
    @Transactional(readOnly = true)
    public PriceQuote priceFor(Offer offer, OfferPaymentMethod method, int installments,
            String couponCode, int commissionBps) {
        CouponDiscount discount = coupons.quote(offer.id(), couponCode, offer.priceCents());
        return price(offer, method, installments, discount, commissionBps);
    }

    private PriceQuote price(Offer offer, OfferPaymentMethod method, int installments,
            CouponDiscount discount, int commissionBps) {
        validate(offer, method, installments);

        long grossCents = offer.priceCents();
        long discountCents = discount.discountCents();
        long paidCents = Math.subtractExact(grossCents, discountCents);
        if (paidCents < TECHNICAL_FLOOR_CENTS) {
            throw new ValidationException("ORDER_AMOUNT_BELOW_MINIMUM",
                    "O valor a pagar ficou abaixo do mínimo aceito para cobrança", "coupon");
        }

        Split split = PriceMath.split(paidCents, method, installments, planOf(offer), commissionBps);

        return new PriceQuote(grossCents, discountCents, paidCents, method, installments,
                split.sellerFeeCents(), split.affiliateCents(), split.sellerCents(),
                OfferAvailability.at(offer, clock.instant()), discount);
    }

    private void validate(Offer offer, OfferPaymentMethod method, int installments) {
        if (method == null) {
            throw new ValidationException("PAYMENT_METHOD_NOT_ALLOWED",
                    "Informe o meio de pagamento", "method");
        }
        if (!offer.paymentMethods().contains(method)) {
            throw new ValidationException("PAYMENT_METHOD_NOT_ALLOWED",
                    "Esta oferta não aceita o meio de pagamento informado", "method");
        }
        if (installments < 1) {
            throw new ValidationException("INSTALLMENTS_NOT_ALLOWED",
                    "O número de parcelas deve ser maior que zero", "installments");
        }
        if (method != OfferPaymentMethod.CARD && installments != 1) {
            throw new ValidationException("INSTALLMENTS_NOT_ALLOWED",
                    "Somente cartão pode ser parcelado", "installments");
        }
        if (installments > offer.maxInstallments()) {
            throw new ValidationException("INSTALLMENTS_NOT_ALLOWED",
                    "Esta oferta aceita no máximo " + offer.maxInstallments() + " parcelas",
                    "installments");
        }
    }

    private Plan planOf(Offer offer) {
        UUID sellerId = sellers.findSellerId(offer.productId())
                .orElseThrow(PriceSimulationService::offerNotFound);
        return Plan.valueOf(plans.currentPlan(sellerId));
    }

    private static NotFoundException offerNotFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada");
    }
}
