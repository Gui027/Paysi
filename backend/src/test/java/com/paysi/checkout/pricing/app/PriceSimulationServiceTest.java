package com.paysi.checkout.pricing.app;

import com.paysi.catalog.coupon.app.CouponDiscount;
import com.paysi.catalog.coupon.app.CouponRedemptionService;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pricing.port.OfferSellerLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.split.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PriceSimulationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final String SLUG = "curso-de-teste";
    private static final UUID OFFER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID SELLER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private OfferRepository offers;
    private CouponRedemptionService coupons;
    private PriceSimulationService service;

    @BeforeEach
    void setUp() {
        offers = mock(OfferRepository.class);
        coupons = mock(CouponRedemptionService.class);
        OfferSellerLookup sellers = mock(OfferSellerLookup.class);
        PlatformPlanReader plans = mock(PlatformPlanReader.class);

        when(offers.findPublishedBySlug(SLUG)).thenReturn(Optional.of(offer(17_700)));
        when(sellers.findSellerId(PRODUCT)).thenReturn(Optional.of(SELLER));
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        when(coupons.quote(any(), any(), anyLong())).thenReturn(CouponDiscount.none());
        when(coupons.reserve(any(), any(), anyLong())).thenReturn(CouponDiscount.none());

        service = new PriceSimulationService(offers, coupons, sellers, plans,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reproduzOExemploDoContratoComAfiliado() {
        // Documento 2, §4.3: 17700 fecha em 14670 + 1770 + 682 + 578.
        PriceQuote quote = service.reserve(offer(17_700), OfferPaymentMethod.CARD, 1, null, 1_000);

        assertThat(quote.paidCents()).isEqualTo(17_700);
        assertThat(quote.sellerCents()).isEqualTo(14_670);
        assertThat(quote.commissionCents()).isEqualTo(1_770);
        assertThat(quote.feesCents()).isEqualTo(1_260);
        assertThat(quote.sellerCents() + quote.commissionCents() + quote.feesCents())
                .isEqualTo(quote.paidCents());
    }

    @Test
    void simulacaoEPedidoDaoOMesmoResultado() {
        when(coupons.quote(OFFER, "PROMO10", 17_700))
                .thenReturn(new CouponDiscount(null, "PROMO10", 1_770, 1));
        when(coupons.reserve(OFFER, "PROMO10", 17_700))
                .thenReturn(new CouponDiscount(UUID.randomUUID(), "PROMO10", 1_770, 1));

        PriceQuote simulated = service.simulate(SLUG, OfferPaymentMethod.PIX, 1, "PROMO10");
        PriceQuote reserved = service.reserve(offer(17_700), OfferPaymentMethod.PIX, 1, "PROMO10", 0);

        assertThat(reserved.grossCents()).isEqualTo(simulated.grossCents());
        assertThat(reserved.discountCents()).isEqualTo(simulated.discountCents());
        assertThat(reserved.paidCents()).isEqualTo(simulated.paidCents());
        assertThat(reserved.feesCents()).isEqualTo(simulated.feesCents());
        assertThat(reserved.sellerCents()).isEqualTo(simulated.sellerCents());
        assertThat(reserved.availableAt()).isEqualTo(simulated.availableAt());
    }

    @Test
    void oBrutoVemSempreDaOferta() {
        PriceQuote quote = service.simulate(SLUG, OfferPaymentMethod.PIX, 1, null);

        assertThat(quote.grossCents()).isEqualTo(17_700);
        assertThat(quote.discountCents()).isZero();
        assertThat(quote.paidCents()).isEqualTo(17_700);
    }

    @Test
    void liberacaoSegueOMaiorEntreRepasseEGarantia() {
        // Repasse D15 supera a garantia de 7 dias, conforme OfferAvailability.
        PriceQuote quote = service.simulate(SLUG, OfferPaymentMethod.PIX, 1, null);

        assertThat(quote.availableAt()).isEqualTo(NOW.plus(15, ChronoUnit.DAYS));
    }

    @Test
    void ofertaNaoPublicadaNaoSimula() {
        when(offers.findPublishedBySlug("rascunho")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.simulate("rascunho", OfferPaymentMethod.PIX, 1, null))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("OFFER_NOT_FOUND"));
    }

    @Test
    void meioNaoAceitoPelaOferta() {
        assertThatThrownBy(() -> service.simulate(SLUG, OfferPaymentMethod.BOLETO, 1, null))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("PAYMENT_METHOD_NOT_ALLOWED");
                    assertThat(error.field()).isEqualTo("method");
                });
    }

    @Test
    void somenteCartaoParcela() {
        assertThatThrownBy(() -> service.simulate(SLUG, OfferPaymentMethod.PIX, 3, null))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("INSTALLMENTS_NOT_ALLOWED"));
    }

    @Test
    void parcelasAcimaDoMaximoDaOferta() {
        assertThatThrownBy(() -> service.simulate(SLUG, OfferPaymentMethod.CARD, 13, null))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("INSTALLMENTS_NOT_ALLOWED"));
    }

    @Test
    void cupomNaoPodeDerrubarAbaixoDoPisoTecnico() {
        when(coupons.quote(OFFER, "QUASETUDO", 17_700))
                .thenReturn(new CouponDiscount(null, "QUASETUDO", 17_300, 1));

        assertThatThrownBy(() -> service.simulate(SLUG, OfferPaymentMethod.PIX, 1, "QUASETUDO"))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("ORDER_AMOUNT_BELOW_MINIMUM"));
    }

    @Test
    void faixasDeTaxaDoCartaoSeguemOParcelamento() {
        assertThat(PriceMath.providerMethod(OfferPaymentMethod.CARD, 1)).isEqualTo(PaymentMethod.CARD_1);
        assertThat(PriceMath.providerMethod(OfferPaymentMethod.CARD, 6)).isEqualTo(PaymentMethod.CARD_6);
        assertThat(PriceMath.providerMethod(OfferPaymentMethod.CARD, 7)).isEqualTo(PaymentMethod.CARD_12);
        assertThat(PriceMath.providerMethod(OfferPaymentMethod.PIX, 1)).isEqualTo(PaymentMethod.PIX);
        assertThat(PriceMath.providerMethod(OfferPaymentMethod.BOLETO, 1)).isEqualTo(PaymentMethod.BOLETO);
    }

    private static Offer offer(long priceCents) {
        return new Offer(OFFER, PRODUCT, ChargeType.ONE_TIME, Segment.DIGITAL, SLUG, priceCents,
                null, 0, true, 7, 12, 3, 5,
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD),
                OfferPayoutDelay.D15, OfferStatus.PUBLISHED, null, NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }
}
