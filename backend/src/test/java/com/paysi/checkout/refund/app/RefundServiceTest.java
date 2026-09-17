package com.paysi.checkout.refund.app;

import com.paysi.checkout.refund.port.RefundRepository;
import com.paysi.checkout.refund.port.RefundRepository.ChargeRefundContext;
import com.paysi.checkout.refund.port.RefundRepository.StoredRefund;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.fiscal.app.InvoiceCancellationService;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.app.LedgerWriteResult;
import com.paysi.payment.provider.PaymentProvider;
import com.paysi.payment.provider.ProviderRefundResult;
import com.paysi.payment.split.RefundPart;
import com.paysi.payment.split.RefundSplit;
import com.paysi.payment.split.Split;
import com.paysi.webhook.app.OutboxService;
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

class RefundServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final long PAID = 17_700;
    private static final long SELLER_AMOUNT = 14_670;
    private static final long AFFILIATE_FEE = 1_770;
    private static final long PLATFORM_FEE = 1_260;
    private static final long PROVIDER_FEE = 578;

    @Test
    void fullRefundReversesSellerAndAffiliateAndMarksChargeRefunded() {
        var fixture = fixture(withAffiliate(0));
        when(fixture.provider.refund(any())).thenReturn(new ProviderRefundResult("prov-refund-1", true, null));
        when(fixture.repository.insertRefund(any(), eq(CHARGE), eq(PAID), anyLong(), anyLong(), anyLong(),
                anyLong(), any(), eq("SUCCEEDED"), eq("prov-refund-1"), eq("idem-1"), eq("SELLER"), eq(NOW),
                eq(NOW))).thenReturn(true);

        var result = fixture.service.refund(SELLER, CHARGE, new RefundCommand(null, "arrependimento", "idem-1"));

        RefundPart expected = expectedPart(0, PAID);
        assertThat(result.sellerCents()).isEqualTo(expected.sellerCents());
        assertThat(result.affiliateCents()).isEqualTo(expected.affiliateCents());
        assertThat(result.chargeStatus()).isEqualTo("REFUNDED");
        assertThat(result.idempotentReplay()).isFalse();

        verify(fixture.ledger).writeCascadeDebit(any(), any(), any(), eq(SELLER), eq(expected.sellerCents()),
                any(), any());
        verify(fixture.ledger).writeCascadeDebit(any(), any(), any(), eq(AFFILIATE), eq(expected.affiliateCents()),
                any(), any());
        verify(fixture.repository).applyChargeRefund(CHARGE, PAID, "REFUNDED");
        verify(fixture.outbox).append(eq(SELLER), eq("payment.refunded"), any());
        verify(fixture.outbox, never()).append(any(), eq("payment.partially_refunded"), any());
        verify(fixture.invoiceCancellation).requestCancellationForRefund(CHARGE);
    }

    @Test
    void partialRefundKeepsChargePartiallyRefundedAndEmitsPartialEvent() {
        var fixture = fixture(withAffiliate(0));
        when(fixture.provider.refund(any())).thenReturn(new ProviderRefundResult("prov-refund-2", true, null));
        when(fixture.repository.insertRefund(any(), eq(CHARGE), eq(8_850L), anyLong(), anyLong(), anyLong(),
                anyLong(), any(), eq("SUCCEEDED"), any(), eq("idem-2"), eq("SELLER"), eq(NOW), eq(NOW)))
                .thenReturn(true);

        var result = fixture.service.refund(SELLER, CHARGE, new RefundCommand(8_850L, "parcial", "idem-2"));

        assertThat(result.chargeStatus()).isEqualTo("PARTIALLY_REFUNDED");
        verify(fixture.repository).applyChargeRefund(CHARGE, 8_850L, "PARTIALLY_REFUNDED");
        verify(fixture.outbox).append(eq(SELLER), eq("payment.partially_refunded"), any());
        verify(fixture.outbox, never()).append(any(), eq("payment.refunded"), any());
    }

    @Test
    void secondPartialRefundAccountsForAlreadyRefundedAmount() {
        var fixture = fixture(withAffiliate(8_850));
        when(fixture.provider.refund(any())).thenReturn(new ProviderRefundResult("prov-refund-3", true, null));
        when(fixture.repository.insertRefund(any(), eq(CHARGE), eq(8_850L), anyLong(), anyLong(), anyLong(),
                anyLong(), any(), eq("SUCCEEDED"), any(), eq("idem-3"), eq("SELLER"), eq(NOW), eq(NOW)))
                .thenReturn(true);

        var result = fixture.service.refund(SELLER, CHARGE, new RefundCommand(null, "resto", "idem-3"));

        assertThat(result.chargeStatus()).isEqualTo("REFUNDED");
        verify(fixture.repository).applyChargeRefund(CHARGE, PAID, "REFUNDED");
    }

    @Test
    void amountExceedingRemainingBalanceIsRejected() {
        var fixture = fixture(withAffiliate(0));

        assertThatThrownBy(() -> fixture.service.refund(SELLER, CHARGE, new RefundCommand(PAID + 1, "x", "idem-4")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void chargeNotFoundFailsFast() {
        var repository = mock(RefundRepository.class);
        var ledger = mock(LedgerService.class);
        var provider = mock(PaymentProvider.class);
        var outbox = mock(OutboxService.class);
        var invoiceCancellation = mock(InvoiceCancellationService.class);
        when(repository.findByIdempotencyKey(CHARGE, "idem-5")).thenReturn(Optional.empty());
        when(repository.lockChargeForRefund(SELLER, CHARGE)).thenReturn(Optional.empty());
        var service = new RefundService(repository, ledger, provider, outbox, invoiceCancellation, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.refund(SELLER, CHARGE, new RefundCommand(null, "x", "idem-5")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void nonRefundableChargeStatusIsRejected() {
        var fixture = fixtureWithStatus("PENDING");

        assertThatThrownBy(() -> fixture.service.refund(SELLER, CHARGE, new RefundCommand(null, "x", "idem-6")))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void replayWithSameIdempotencyKeyDoesNotCallProviderOrLedgerAgain() {
        var repository = mock(RefundRepository.class);
        var ledger = mock(LedgerService.class);
        var provider = mock(PaymentProvider.class);
        var outbox = mock(OutboxService.class);
        var invoiceCancellation = mock(InvoiceCancellationService.class);
        var stored = new StoredRefund(UUID.randomUUID(), PAID, SELLER_AMOUNT, AFFILIATE_FEE,
                PLATFORM_FEE - PROVIDER_FEE, PROVIDER_FEE, "SUCCEEDED", PAID, "REFUNDED");
        when(repository.findByIdempotencyKey(CHARGE, "idem-7")).thenReturn(Optional.of(stored));
        var service = new RefundService(repository, ledger, provider, outbox, invoiceCancellation, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.refund(SELLER, CHARGE, new RefundCommand(null, "x", "idem-7"));

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.chargeStatus()).isEqualTo("REFUNDED");
        verifyNoInteractions(provider, ledger);
        verify(repository, never()).lockChargeForRefund(any(), any());
    }

    @Test
    void replayWithDifferentAmountThanStoredConflicts() {
        var repository = mock(RefundRepository.class);
        var ledger = mock(LedgerService.class);
        var provider = mock(PaymentProvider.class);
        var outbox = mock(OutboxService.class);
        var invoiceCancellation = mock(InvoiceCancellationService.class);
        var stored = new StoredRefund(UUID.randomUUID(), 8_850, 7_335, 885, 341, 289, "SUCCEEDED", 8_850,
                "PARTIALLY_REFUNDED");
        when(repository.findByIdempotencyKey(CHARGE, "idem-8")).thenReturn(Optional.of(stored));
        var service = new RefundService(repository, ledger, provider, outbox, invoiceCancellation, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.refund(SELLER, CHARGE, new RefundCommand(17_700L, "x", "idem-8")))
                .isInstanceOf(ConflictException.class);
    }

    private static RefundPart expectedPart(long alreadyRefunded, long slice) {
        Split original = new Split(SELLER_AMOUNT, AFFILIATE_FEE, PLATFORM_FEE - PROVIDER_FEE, PROVIDER_FEE,
                PLATFORM_FEE);
        return RefundSplit.slice(original, PAID, alreadyRefunded, slice);
    }

    private static Fixture fixture(ChargeRefundContext context) {
        var repository = mock(RefundRepository.class);
        var ledger = mock(LedgerService.class);
        var provider = mock(PaymentProvider.class);
        var outbox = mock(OutboxService.class);
        var invoiceCancellation = mock(InvoiceCancellationService.class);
        when(repository.findByIdempotencyKey(eq(CHARGE), any())).thenReturn(Optional.empty());
        when(repository.lockChargeForRefund(SELLER, CHARGE)).thenReturn(Optional.of(context));
        when(ledger.writeCascadeDebit(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new LedgerWriteResult(UUID.randomUUID(), false));
        var service = new RefundService(repository, ledger, provider, outbox, invoiceCancellation, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, ledger, provider, outbox, invoiceCancellation);
    }

    private static Fixture fixtureWithStatus(String status) {
        return fixture(new ChargeRefundContext(SELLER, AFFILIATE, "prov-charge-1", PAID, 0, status, SELLER_AMOUNT,
                AFFILIATE_FEE, PLATFORM_FEE, PROVIDER_FEE));
    }

    private static ChargeRefundContext withAffiliate(long refundedCents) {
        return new ChargeRefundContext(SELLER, AFFILIATE, "prov-charge-1", PAID, refundedCents, "PAID",
                SELLER_AMOUNT, AFFILIATE_FEE, PLATFORM_FEE, PROVIDER_FEE);
    }

    private record Fixture(RefundService service, RefundRepository repository, LedgerService ledger,
                           PaymentProvider provider, OutboxService outbox,
                           InvoiceCancellationService invoiceCancellation) {
    }
}
