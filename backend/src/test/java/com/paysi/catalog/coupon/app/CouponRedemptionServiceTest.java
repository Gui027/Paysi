package com.paysi.catalog.coupon.app;

import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.port.CouponRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
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

class CouponRedemptionServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00Z");
    private static final UUID OFFER = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID BUYER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ORDER = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID SELLER = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private CouponRepository coupons;
    private CouponRedemptionService service;

    @BeforeEach
    void setUp() {
        coupons = mock(CouponRepository.class);
        service = new CouponRedemptionService(coupons, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void semCodigoNaoConsultaCupomENaoDaDesconto() {
        assertThat(service.quote(OFFER, null, 17_700).discountCents()).isZero();
        assertThat(service.reserve(OFFER, "  ", 17_700).discountCents()).isZero();
        verify(coupons, never()).findApplicable(any(), any());
        verify(coupons, never()).reserve(any(), any());
    }

    @Test
    void normalizaCodigoParaMaiusculas() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, null, 1, 0));
        when(coupons.reserve(any(), eq(NOW))).thenReturn(true);

        service.reserve(OFFER, " promo10 ", 17_700);

        verify(coupons).findApplicable(OFFER, "PROMO10");
    }

    @Test
    void descontoPercentualTruncaEmCentavos() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, null, 1, 0));

        // 10% de 17.705 = 1.770,5 centavos; trunca para baixo.
        assertThat(service.quote(OFFER, "PROMO10", 17_705).discountCents()).isEqualTo(1_770);
    }

    @Test
    void descontoFixoNuncaUltrapassaOBruto() {
        stub(coupon(CouponKind.FIXED, 30_000, null, null, null, 1, 0));

        assertThat(service.quote(OFFER, "TUDO", 17_700).discountCents()).isEqualTo(17_700);
    }

    @Test
    void simulacaoNaoConsomeUnidade() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, 50, 1, 0));

        CouponDiscount discount = service.quote(OFFER, "PROMO10", 17_700);

        assertThat(discount.reserved()).isFalse();
        verify(coupons, never()).reserve(any(), any());
    }

    @Test
    void cupomInexistenteParaAOferta() {
        when(coupons.findApplicable(OFFER, "PROMO10")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.quote(OFFER, "PROMO10", 17_700))
                .isInstanceOfSatisfying(NotFoundException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_NOT_FOUND"));
    }

    @Test
    void cupomVencido() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, NOW, null, 1, 0));

        assertThatThrownBy(() -> service.quote(OFFER, "PROMO10", 17_700))
                .isInstanceOfSatisfying(ConflictException.class, error -> {
                    assertThat(error.code()).isEqualTo("COUPON_EXPIRED");
                    assertThat(error.field()).isEqualTo("coupon");
                });
    }

    @Test
    void cupomAindaNaoIniciado() {
        stub(coupon(CouponKind.PERCENT, 1_000, NOW.plusSeconds(1), null, null, 1, 0));

        assertThatThrownBy(() -> service.quote(OFFER, "PROMO10", 17_700))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_NOT_STARTED"));
    }

    @Test
    void cupomEsgotado() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, 50, 1, 50));

        assertThatThrownBy(() -> service.reserve(OFFER, "PROMO10", 17_700))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_EXHAUSTED"));
        verify(coupons, never()).reserve(any(), any());
    }

    @Test
    void perderACorridaNoUpdateCondicionalTambemEsgota() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, 50, 1, 49));
        when(coupons.reserve(any(), eq(NOW))).thenReturn(false);

        assertThatThrownBy(() -> service.reserve(OFFER, "PROMO10", 17_700))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_EXHAUSTED"));
    }

    @Test
    void confirmacaoGravaTrilhaAntesDeConferirLimitePorComprador() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, null, 2, 0));
        when(coupons.reserve(any(), eq(NOW))).thenReturn(true);
        CouponDiscount discount = service.reserve(OFFER, "PROMO10", 17_700);
        when(coupons.countRedemptionsByBuyer(discount.couponId(), BUYER)).thenReturn(2);

        service.confirm(discount, ORDER, BUYER);

        verify(coupons).recordRedemption(discount.couponId(), ORDER, BUYER, 1_770);
    }

    @Test
    void limitePorCompradorEstouradoDerrubaATransacao() {
        stub(coupon(CouponKind.PERCENT, 1_000, null, null, null, 1, 0));
        when(coupons.reserve(any(), eq(NOW))).thenReturn(true);
        CouponDiscount discount = service.reserve(OFFER, "PROMO10", 17_700);
        when(coupons.countRedemptionsByBuyer(discount.couponId(), BUYER)).thenReturn(2);

        assertThatThrownBy(() -> service.confirm(discount, ORDER, BUYER))
                .isInstanceOfSatisfying(ConflictException.class,
                        error -> assertThat(error.code()).isEqualTo("COUPON_LIMIT_REACHED"));
    }

    @Test
    void confirmacaoSemCupomNaoEscreveNada() {
        service.confirm(CouponDiscount.none(), ORDER, BUYER);

        verify(coupons, never()).recordRedemption(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    private void stub(Coupon coupon) {
        when(coupons.findApplicable(eq(OFFER), any())).thenReturn(Optional.of(coupon));
    }

    private static Coupon coupon(CouponKind kind, int value, Instant startsAt, Instant expiresAt,
            Integer maxRedemptions, int maxPerBuyer, int redeemedCount) {
        return new Coupon(UUID.randomUUID(), SELLER, "PROMO10", kind, value, startsAt, expiresAt,
                maxRedemptions, maxPerBuyer, redeemedCount, Set.of(OFFER), null, NOW.minusSeconds(60));
    }
}
