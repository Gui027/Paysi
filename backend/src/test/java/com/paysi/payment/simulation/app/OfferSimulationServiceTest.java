package com.paysi.payment.simulation.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.simulation.web.dto.OfferSimulationRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfferSimulationServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    void simulatesDirectSaleWithServerSplitAndAvailability() {
        OfferRepository offers = mock(OfferRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        PlatformPlanReader plans = mock(PlatformPlanReader.class);
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(offer(14, OfferPayoutDelay.D7)));
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");

        var result = service(offers, coupons, plans).simulate(SELLER, OFFER_ID,
                new OfferSimulationRequest(OfferPaymentMethod.PIX, 1, null));

        assertThat(result.grossCents()).isEqualTo(10_000);
        assertThat(result.discountCents()).isZero();
        assertThat(result.paidCents()).isEqualTo(10_000);
        assertThat(result.commissionCents()).isZero();
        assertThat(result.sellerCents()).isEqualTo(9_401);
        assertThat(result.availableAt()).isEqualTo(NOW.plusSeconds(14L * 86_400));
    }

    @Test
    void appliesCouponWithoutConsumingIt() {
        OfferRepository offers = mock(OfferRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        PlatformPlanReader plans = mock(PlatformPlanReader.class);
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(offer(7, OfferPayoutDelay.D15)));
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        Coupon coupon = new Coupon(UUID.randomUUID(), SELLER, "PROMO10", CouponKind.PERCENT, 1_000,
                null, null, 10, 1, 0, Set.of(OFFER_ID), null, NOW);
        when(coupons.findApplicableOwned(SELLER, OFFER_ID, "PROMO10")).thenReturn(Optional.of(coupon));

        var result = service(offers, coupons, plans).simulate(SELLER, OFFER_ID,
                new OfferSimulationRequest(OfferPaymentMethod.CARD, 1, " promo10 "));

        assertThat(result.discountCents()).isEqualTo(1_000);
        assertThat(result.paidCents()).isEqualTo(9_000);
        assertThat(result.commissionCents()).isZero();
        verify(coupons, never()).update(any());
    }

    @Test
    void rejectsForeignOfferAndUnavailableMethod() {
        OfferRepository offers = mock(OfferRepository.class);
        CouponRepository coupons = mock(CouponRepository.class);
        PlatformPlanReader plans = mock(PlatformPlanReader.class);
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(offers, coupons, plans).simulate(SELLER, OFFER_ID,
                new OfferSimulationRequest(OfferPaymentMethod.PIX, 1, null)))
                .isInstanceOf(NotFoundException.class);

        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(offer(7, OfferPayoutDelay.D7)));
        assertThatThrownBy(() -> service(offers, coupons, plans).simulate(SELLER, OFFER_ID,
                new OfferSimulationRequest(OfferPaymentMethod.BOLETO, 1, null)))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_PAYMENT_METHOD_INVALID"));
    }

    private static OfferSimulationService service(OfferRepository offers, CouponRepository coupons,
                                                   PlatformPlanReader plans) {
        return new OfferSimulationService(offers, coupons, plans, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Offer offer(int guaranteeDays, OfferPayoutDelay payoutDelay) {
        return new Offer(OFFER_ID, UUID.randomUUID(), ChargeType.ONE_TIME, Segment.DIGITAL,
                "produto-12345678", 10_000, null, 0, true, guaranteeDays, 12, 3, 5,
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD), payoutDelay,
                OfferStatus.DRAFT, null, NOW, NOW);
    }
}
