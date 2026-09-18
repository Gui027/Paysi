package com.paysi.checkout.dispute.app;

import com.paysi.checkout.dispute.port.DisputeRepository;
import com.paysi.checkout.dispute.port.DisputeRepository.BucketAmount;
import com.paysi.checkout.dispute.port.DisputeRepository.ChargeDisputeContext;
import com.paysi.checkout.dispute.port.DisputeRepository.StoredDispute;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.app.LedgerWriteResult;
import com.paysi.risk.app.RiskService;
import com.paysi.risk.app.SellerRiskSnapshot;
import com.paysi.webhook.app.OutboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DisputeServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final long PAID = 10_000;
    private static final long SELLER_AMOUNT = 8_201;
    private static final long AFFILIATE_FEE = 1_000;
    private static final long PLATFORM_FEE = 451;
    private static final long PROVIDER_FEE = 348;

    @Test
    void openDisputeDebitsSellerWithReserveFirstAndAbsorbsPlatformShareAndFee() {
        var fixture = fixture(withAffiliate());
        when(fixture.repository.insertDispute(any(), eq(CHARGE), eq(PAID), eq(3_000L), any(), eq("OPEN"), any(),
                eq("prov-dispute-1"), eq(NOW))).thenReturn(true);

        var result = fixture.service.open(SELLER, CHARGE,
                new DisputeCommand("prov-dispute-1", "fraude alegada", null, 3_000L, null));

        // seller absorve o próprio share + platform + provider + tarifa da adquirente
        assertThat(result.sellerCents()).isEqualTo(SELLER_AMOUNT + PLATFORM_FEE + PROVIDER_FEE + 3_000L);
        assertThat(result.affiliateCents()).isEqualTo(AFFILIATE_FEE);
        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.idempotentReplay()).isFalse();

        verify(fixture.ledger).writeDisputeCascadeDebit(eq(com.paysi.ledger.domain.TransactionType.CHARGEBACK),
                any(), any(), eq(SELLER), eq(SELLER_AMOUNT + PLATFORM_FEE + PROVIDER_FEE + 3_000L), any(), any());
        verify(fixture.ledger).writeCascadeDebit(eq(com.paysi.ledger.domain.TransactionType.CHARGEBACK), any(),
                any(), eq(AFFILIATE), eq(AFFILIATE_FEE), any(), any());
        verify(fixture.repository).applyChargeDisputeStatus(CHARGE, "CHARGEBACK");
        verify(fixture.outbox).append(eq(SELLER), eq("chargeback.opened"), any());
        verify(fixture.risk).recalculateSeller(SELLER);
    }

    @Test
    void openDisputeComputesDefaultSevenDayDeadlineWhenNotProvided() {
        var fixture = fixture(withAffiliate());
        when(fixture.repository.insertDispute(any(), eq(CHARGE), anyLong(), anyLong(), any(), eq("OPEN"), any(),
                any(), eq(NOW))).thenReturn(true);

        var result = fixture.service.open(SELLER, CHARGE, new DisputeCommand("prov-dispute-2", "x", null, null,
                null));

        assertThat(result.deadlineAt()).isEqualTo(NOW.plusSeconds(7L * 24 * 3600));
    }

    @Test
    void amountExceedingRemainingBalanceIsRejected() {
        var fixture = fixture(withAffiliate());

        assertThatThrownBy(() -> fixture.service.open(SELLER, CHARGE,
                new DisputeCommand("prov-dispute-3", "x", PAID + 1, null, null)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.ledger, fixture.outbox, fixture.risk);
    }

    @Test
    void missingProviderDisputeIdIsRejected() {
        var fixture = fixture(withAffiliate());

        assertThatThrownBy(() -> fixture.service.open(SELLER, CHARGE, new DisputeCommand(" ", "x", null, null,
                null))).isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.repository, fixture.ledger);
    }

    @Test
    void chargeNotOwnedBySellerFailsFast() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        when(repository.findByProviderDisputeId("prov-4")).thenReturn(Optional.empty());
        when(repository.lockChargeForDispute(SELLER, CHARGE)).thenReturn(Optional.empty());
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.open(SELLER, CHARGE, new DisputeCommand("prov-4", "x", null, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void nonDisputableChargeStatusIsRejected() {
        var fixture = fixtureWithStatus("PENDING");

        assertThatThrownBy(() -> fixture.service.open(SELLER, CHARGE,
                new DisputeCommand("prov-5", "x", null, null, null))).isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.ledger);
    }

    @Test
    void replayWithSameProviderDisputeIdDoesNotWriteLedgerAgain() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        var stored = new StoredDispute(UUID.randomUUID(), CHARGE, SELLER, AFFILIATE, PAID, 3_000, "x", "OPEN",
                NOW.plusSeconds(1), "prov-6", NOW);
        when(repository.findByProviderDisputeId("prov-6")).thenReturn(Optional.of(stored));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.open(SELLER, CHARGE, new DisputeCommand("prov-6", "x", null, null, null));

        assertThat(result.idempotentReplay()).isTrue();
        assertThat(result.status()).isEqualTo("OPEN");
        verifyNoInteractions(ledger, risk, outbox);
        verify(repository, never()).lockChargeForDispute(any(), any());
    }

    @Test
    void replayWithDifferentAmountThanStoredConflicts() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        var stored = new StoredDispute(UUID.randomUUID(), CHARGE, SELLER, AFFILIATE, PAID, 3_000, "x", "OPEN",
                NOW.plusSeconds(1), "prov-7", NOW);
        when(repository.findByProviderDisputeId("prov-7")).thenReturn(Optional.of(stored));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.open(SELLER, CHARGE,
                new DisputeCommand("prov-7", "x", PAID - 1, null, null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void wonReversalCreditsExactOriginalAllocationAndRevertsChargeStatus() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        UUID disputeId = UUID.randomUUID();
        var stored = new StoredDispute(disputeId, CHARGE, SELLER, AFFILIATE, 10_000, 3_000, "x", "OPEN",
                NOW.plusSeconds(1), "prov-8", NOW);
        when(repository.lockDisputeForResolution(SELLER, disputeId)).thenReturn(Optional.of(stored));
        when(repository.sellerOpeningAllocation(disputeId)).thenReturn(List.of(
                new BucketAmount("RESERVE", 328), new BucketAmount("AVAILABLE", 5_000),
                new BucketAmount("DEBT", 6_672)));
        when(repository.affiliateOpeningAllocation(disputeId)).thenReturn(
                List.of(new BucketAmount("AVAILABLE", 1_000)));
        when(ledger.write(any())).thenReturn(new LedgerWriteResult(UUID.randomUUID(), false));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.resolve(SELLER, disputeId, DisputeOutcome.WON, "defesa aceita");

        assertThat(result.status()).isEqualTo("WON");
        verify(ledger).write(argThat(command -> {
            long credits = command.entries().stream()
                    .filter(e -> e.direction() == com.paysi.ledger.domain.Direction.CREDIT)
                    .mapToLong(com.paysi.ledger.domain.LedgerEntry::amountCents).sum();
            long debits = command.entries().stream()
                    .filter(e -> e.direction() == com.paysi.ledger.domain.Direction.DEBIT)
                    .mapToLong(com.paysi.ledger.domain.LedgerEntry::amountCents).sum();
            return credits == debits && credits == 13_000;
        }));
        verify(repository).applyChargeDisputeStatus(CHARGE, "PAID");
        verify(repository).updateDisputeStatus(disputeId, "WON");
        verify(risk).recalculateSeller(SELLER);
    }

    @Test
    void lostDisputeKeepsDebitFinalWithNoLedgerReversal() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        UUID disputeId = UUID.randomUUID();
        var stored = new StoredDispute(disputeId, CHARGE, SELLER, AFFILIATE, 10_000, 3_000, "x", "OPEN",
                NOW.plusSeconds(1), "prov-9", NOW);
        when(repository.lockDisputeForResolution(SELLER, disputeId)).thenReturn(Optional.of(stored));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.resolve(SELLER, disputeId, DisputeOutcome.LOST, "sem defesa suficiente");

        assertThat(result.status()).isEqualTo("LOST");
        verifyNoInteractions(ledger);
        verify(repository, never()).applyChargeDisputeStatus(any(), any());
        verify(repository).updateDisputeStatus(disputeId, "LOST");
        verify(risk).recalculateSeller(SELLER);
    }

    @Test
    void resolvingAlreadyResolvedDisputeWithDifferentOutcomeConflicts() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        UUID disputeId = UUID.randomUUID();
        var stored = new StoredDispute(disputeId, CHARGE, SELLER, AFFILIATE, 10_000, 3_000, "x", "LOST",
                NOW.plusSeconds(1), "prov-10", NOW);
        when(repository.lockDisputeForResolution(SELLER, disputeId)).thenReturn(Optional.of(stored));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.resolve(SELLER, disputeId, DisputeOutcome.WON, "x"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void resolvingWithSameOutcomeReplaysIdempotently() {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        UUID disputeId = UUID.randomUUID();
        var stored = new StoredDispute(disputeId, CHARGE, SELLER, AFFILIATE, 10_000, 3_000, "x", "LOST",
                NOW.plusSeconds(1), "prov-11", NOW);
        when(repository.lockDisputeForResolution(SELLER, disputeId)).thenReturn(Optional.of(stored));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.resolve(SELLER, disputeId, DisputeOutcome.LOST, "x");

        assertThat(result.idempotentReplay()).isTrue();
        verifyNoInteractions(ledger, risk);
    }

    private static Fixture fixture(ChargeDisputeContext context) {
        var repository = mock(DisputeRepository.class);
        var ledger = mock(LedgerService.class);
        var risk = mock(RiskService.class);
        var outbox = mock(OutboxService.class);
        when(repository.findByProviderDisputeId(any())).thenReturn(Optional.empty());
        when(repository.lockChargeForDispute(SELLER, CHARGE)).thenReturn(Optional.of(context));
        when(ledger.writeDisputeCascadeDebit(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new LedgerWriteResult(UUID.randomUUID(), false));
        when(ledger.writeCascadeDebit(any(), any(), any(), any(), anyLong(), any(), any()))
                .thenReturn(new LedgerWriteResult(UUID.randomUUID(), false));
        when(risk.recalculateSeller(any())).thenReturn(new SellerRiskSnapshot(SELLER, 0, 0, null, null));
        var service = new DisputeService(repository, ledger, risk, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository, ledger, risk, outbox);
    }

    private static Fixture fixtureWithStatus(String status) {
        return fixture(new ChargeDisputeContext(SELLER, AFFILIATE, PAID, 0, status, SELLER_AMOUNT, AFFILIATE_FEE,
                PLATFORM_FEE, PROVIDER_FEE));
    }

    private static ChargeDisputeContext withAffiliate() {
        return new ChargeDisputeContext(SELLER, AFFILIATE, PAID, 0, "PAID", SELLER_AMOUNT, AFFILIATE_FEE,
                PLATFORM_FEE, PROVIDER_FEE);
    }

    private record Fixture(DisputeService service, DisputeRepository repository, LedgerService ledger,
                            RiskService risk, OutboxService outbox) {
    }
}
