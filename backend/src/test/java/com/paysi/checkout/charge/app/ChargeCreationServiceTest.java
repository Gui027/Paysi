package com.paysi.checkout.charge.app;

import com.paysi.affiliate.app.CommissionService;
import com.paysi.checkout.charge.port.ChargeCreationRepository;
import com.paysi.checkout.charge.port.ChargeCreationRepository.OrderContext;
import com.paysi.core.error.NotFoundException;
import com.paysi.payment.boleto.app.BoletoPaymentService;
import com.paysi.payment.boleto.domain.BoletoResult;
import com.paysi.payment.card.app.CardPaymentService;
import com.paysi.payment.card.domain.CardPaymentCommand;
import com.paysi.payment.card.domain.CardPaymentResult;
import com.paysi.payment.pix.app.PixPaymentService;
import com.paysi.payment.pix.domain.PixResult;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChargeCreationServiceTest {
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void createsChargeAndStartsCardPaymentOnFirstCall() {
        var fixture = fixture(context("CARD", null, 0));
        when(fixture.repository.findChargeForOrder(ORDER)).thenReturn(Optional.empty());
        when(fixture.cardPayments.start(any(), any())).thenReturn(cardResult("approved", false));

        var result = fixture.service.start(ORDER, command("tok_1"));

        assertThat(result.method()).isEqualTo("CARD");
        assertThat(result.card().status()).isEqualTo("approved");
        verify(fixture.repository).insertCharge(any(), eq(ORDER), eq(10_000L), any(), anyInt(), eq(200L),
                anyLong(), anyLong(), anyLong(), eq("PENDING"), eq(NOW));
        verifyNoInteractions(fixture.commissions);
    }

    @Test
    void reusesExistingChargeInsteadOfCreatingANewOne() {
        var fixture = fixture(context("CARD", null, 0));
        when(fixture.repository.findChargeForOrder(ORDER)).thenReturn(Optional.of(CHARGE));
        when(fixture.cardPayments.start(eq(CHARGE), any())).thenReturn(cardResult("approved", true));

        var result = fixture.service.start(ORDER, command("tok_1"));

        assertThat(result.chargeId()).isEqualTo(CHARGE);
        verify(fixture.repository, never()).insertCharge(any(), any(), anyLong(), any(), anyInt(), anyLong(),
                anyLong(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void liquidatesAffiliateCommissionOnlyWhenCardIsApproved() {
        var fixture = fixture(context("CARD", AFFILIATE, 1_000));
        when(fixture.repository.findChargeForOrder(ORDER)).thenReturn(Optional.of(CHARGE));
        when(fixture.cardPayments.start(eq(CHARGE), any())).thenReturn(cardResult("declined", false));

        fixture.service.start(ORDER, command("tok_1"));
        verifyNoInteractions(fixture.commissions);

        when(fixture.cardPayments.start(eq(CHARGE), any())).thenReturn(cardResult("approved", false));
        fixture.service.start(ORDER, command("tok_1"));
        verify(fixture.commissions).liquidate(eq(AFFILIATE), anyLong(), eq(CHARGE), eq(NOW), eq(7));
    }

    @Test
    void dispatchesToBoletoUsingOfferDueDays() {
        var fixture = fixture(context("BOLETO", null, 0));
        when(fixture.repository.findChargeForOrder(ORDER)).thenReturn(Optional.of(CHARGE));
        when(fixture.boletoPayments.issue(CHARGE, 3)).thenReturn(
                new BoletoResult("prov-1", "34191", "https://boleto", NOW, "PENDING", false));

        var result = fixture.service.start(ORDER, command(null));

        assertThat(result.method()).isEqualTo("BOLETO");
        assertThat(result.boleto().barcode()).isEqualTo("34191");
        verifyNoInteractions(fixture.cardPayments);
    }

    @Test
    void dispatchesToPix() {
        var fixture = fixture(context("PIX", null, 0));
        when(fixture.repository.findChargeForOrder(ORDER)).thenReturn(Optional.of(CHARGE));
        when(fixture.pixPayments.start(CHARGE)).thenReturn(new PixResult("prov-1", "000201", NOW, "PENDING", false));

        var result = fixture.service.start(ORDER, command(null));

        assertThat(result.method()).isEqualTo("PIX");
        assertThat(result.pix().qrCode()).isEqualTo("000201");
    }

    @Test
    void ordersThatDoNotExistFailFast() {
        var fixture = fixture(context("CARD", null, 0));
        when(fixture.repository.findOrderContext(ORDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.start(ORDER, command("tok_1")))
                .isInstanceOf(NotFoundException.class);
    }

    private static Fixture fixture(OrderContext context) {
        var repository = mock(ChargeCreationRepository.class);
        var plans = mock(com.paysi.identity.port.PlatformPlanReader.class);
        var commissions = mock(CommissionService.class);
        var cardPayments = mock(CardPaymentService.class);
        var boletoPayments = mock(BoletoPaymentService.class);
        var pixPayments = mock(PixPaymentService.class);
        when(repository.findOrderContext(ORDER)).thenReturn(Optional.of(context));
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        var service = new ChargeCreationService(repository, plans, commissions, cardPayments, boletoPayments,
                pixPayments, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, commissions, cardPayments, boletoPayments, pixPayments);
    }

    private static OrderContext context(String method, UUID affiliateId, int commissionBps) {
        return new OrderContext(SELLER, affiliateId, commissionBps, 10_000, method, 1, "Comprador",
                "buyer@example.com", "PF", "52998224725", 3, 7);
    }

    private static StartChargeCommand command(String cardToken) {
        return new StartChargeCommand(cardToken, "127.0.0.1", "agent", "device-1", "terms-v1", NOW);
    }

    private static CardPaymentResult cardResult(String status, boolean replay) {
        return new CardPaymentResult("prov-1", status,
                new CardPaymentResult.CardThreeDs(false, "NOT_APPLICABLE", null, null), null, replay);
    }

    private record Fixture(ChargeCreationService service, ChargeCreationRepository repository,
                           CommissionService commissions, CardPaymentService cardPayments,
                           BoletoPaymentService boletoPayments, PixPaymentService pixPayments) {
    }
}
