package com.paysi.payment.simulation.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.catalog.offer.app.OfferAvailability;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.simulation.web.dto.OfferSimulationRequest;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class OfferSimulationService {
    private final OfferRepository offers;
    private final CouponRepository coupons;
    private final PlatformPlanReader plans;
    private final Clock clock;

    @Autowired
    public OfferSimulationService(OfferRepository offers, CouponRepository coupons,
                                  PlatformPlanReader plans) {
        this(offers, coupons, plans, Clock.systemUTC());
    }

    OfferSimulationService(OfferRepository offers, CouponRepository coupons,
                           PlatformPlanReader plans, Clock clock) {
        this.offers = offers;
        this.coupons = coupons;
        this.plans = plans;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OfferSimulation simulate(UUID sellerId, UUID offerId, OfferSimulationRequest request) {
        Offer offer = offers.findActiveOwned(sellerId, offerId).orElseThrow(() ->
                new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));
        validateMethod(offer, request);

        Instant now = clock.instant();
        long grossCents = offer.priceCents();
        long discountCents = discount(sellerId, offer, request.couponCode(), grossCents, now);
        long paidCents = Math.subtractExact(grossCents, discountCents);
        if (paidCents < 500) {
            throw new ValidationException("SIMULATION_VALUE_TOO_LOW",
                    "O valor pago não pode ser menor que R$ 5,00", "couponCode");
        }

        Split split = SplitEngine.split(paidCents, paymentMethod(request),
                Plan.valueOf(plans.currentPlan(sellerId)), 0);
        return new OfferSimulation(grossCents, discountCents, paidCents,
                split.sellerFeeCents(), split.providerCostCents(), split.affiliateCents(),
                split.sellerCents(), OfferAvailability.at(offer, now));
    }

    private long discount(UUID sellerId, Offer offer, String rawCode, long grossCents, Instant now) {
        if (rawCode == null || rawCode.isBlank()) return 0;
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        Coupon coupon = coupons.findApplicableOwned(sellerId, offer.id(), code).orElseThrow(() ->
                new ValidationException("COUPON_NOT_FOUND", "Cupom não encontrado para esta oferta", "couponCode"));
        if (coupon.startsAt() != null && now.isBefore(coupon.startsAt())) {
            throw new ValidationException("COUPON_NOT_STARTED", "O cupom ainda não está disponível", "couponCode");
        }
        if (coupon.expiresAt() != null && !now.isBefore(coupon.expiresAt())) {
            throw new ValidationException("COUPON_EXPIRED", "O cupom expirou", "couponCode");
        }
        if (coupon.maxRedemptions() != null && coupon.redeemedCount() >= coupon.maxRedemptions()) {
            throw new ValidationException("COUPON_EXHAUSTED", "O cupom atingiu o limite de usos", "couponCode");
        }
        return coupon.discountCents(grossCents);
    }

    private static void validateMethod(Offer offer, OfferSimulationRequest request) {
        if (!offer.paymentMethods().contains(request.method())) {
            throw new ValidationException("OFFER_PAYMENT_METHOD_INVALID",
                    "Este meio de pagamento não está habilitado na oferta", "method");
        }
        int installments = request.installments() == null ? 0 : request.installments();
        if (installments < 1 || (request.method() != OfferPaymentMethod.CARD && installments != 1)
                || installments > offer.maxInstallments()) {
            throw new ValidationException("OFFER_INSTALLMENTS_INVALID",
                    "O parcelamento não é compatível com a oferta", "installments");
        }
    }

    private static PaymentMethod paymentMethod(OfferSimulationRequest request) {
        return switch (request.method()) {
            case PIX -> PaymentMethod.PIX;
            case BOLETO -> PaymentMethod.BOLETO;
            case CARD -> request.installments() == 1 ? PaymentMethod.CARD_1
                    : request.installments() <= 6 ? PaymentMethod.CARD_6 : PaymentMethod.CARD_12;
        };
    }

    public record OfferSimulation(long grossCents, long discountCents, long paidCents,
                                  long platformFeeCents, long providerCostCents,
                                  long commissionCents, long sellerCents, Instant availableAt) { }
}
