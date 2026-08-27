package com.paysi.ledger.jobs.app;

import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.app.LedgerWriteResult;
import com.paysi.ledger.domain.*;
import com.paysi.ledger.jobs.domain.DueRelease;
import com.paysi.ledger.jobs.port.LedgerReleaseRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LedgerReleaseProcessorTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Instant SOURCE_AT = Instant.parse("2026-08-01T12:00:00Z");
    private static final Instant RECEIPT_AT = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void guaranteeReleaseCompensatesDebtBeforePending() {
        var command = processor(mock(LedgerReleaseRepository.class), mock(LedgerService.class))
                .command(due(Bucket.GUARANTEE, Origin.SALE, 10_000, "D32"), 2_500);

        assertThat(command.type()).isEqualTo(TransactionType.GUARANTEE_RELEASE);
        assertThat(command.entries()).containsExactly(
                entry(Bucket.GUARANTEE, Direction.DEBIT, 10_000, null, Origin.SALE),
                entry(Bucket.DEBT, Direction.CREDIT, 2_500, null, Origin.DEBT),
                entry(Bucket.PENDING, Direction.CREDIT, 7_500, RECEIPT_AT, Origin.SALE));
    }

    @Test
    void pendingReleaseCreatesSellerReserveForNinetyDays() {
        var command = processor(mock(LedgerReleaseRepository.class), mock(LedgerService.class))
                .command(due(Bucket.PENDING, Origin.SALE, 10_000, "D7"), 0);

        assertThat(command.type()).isEqualTo(TransactionType.RELEASE);
        assertThat(command.entries()).containsExactly(
                entry(Bucket.PENDING, Direction.DEBIT, 10_000, null, Origin.SALE),
                entry(Bucket.AVAILABLE, Direction.CREDIT, 9_200, null, Origin.SALE),
                entry(Bucket.RESERVE, Direction.CREDIT, 800, SOURCE_AT.plusSeconds(90L * 86_400), Origin.SALE));
    }

    @Test
    void affiliateCommissionDoesNotCreateReserve() {
        var command = processor(mock(LedgerReleaseRepository.class), mock(LedgerService.class))
                .command(due(Bucket.PENDING, Origin.COMMISSION, 1_000, "D2"), 0);

        assertThat(command.entries()).containsExactly(
                entry(Bucket.PENDING, Direction.DEBIT, 1_000, null, Origin.COMMISSION),
                entry(Bucket.AVAILABLE, Direction.CREDIT, 1_000, null, Origin.COMMISSION));
    }

    @Test
    void reserveReleaseMovesEntireAmountToAvailable() {
        var command = processor(mock(LedgerReleaseRepository.class), mock(LedgerService.class))
                .command(due(Bucket.RESERVE, Origin.SALE, 800, "D7"), 0);

        assertThat(command.type()).isEqualTo(TransactionType.RESERVE_RELEASE);
        assertThat(command.entries()).containsExactly(
                entry(Bucket.RESERVE, Direction.DEBIT, 800, null, Origin.SALE),
                entry(Bucket.AVAILABLE, Direction.CREDIT, 800, null, Origin.SALE));
    }

    @Test
    void writesAndMarksClaimedReleaseInTheSameOperation() {
        var repository = mock(LedgerReleaseRepository.class);
        var ledger = mock(LedgerService.class);
        var due = due(Bucket.GUARANTEE, Origin.SALE, 10_000, "D32");
        when(repository.claimNext(Bucket.GUARANTEE, NOW)).thenReturn(Optional.of(due));
        when(repository.lockAndReadDebt(ACCOUNT_ID)).thenReturn(2_500L);
        when(ledger.write(any())).thenReturn(new LedgerWriteResult(TRANSACTION_ID, false));
        when(repository.markReleased(10, TRANSACTION_ID, NOW)).thenReturn(true);

        assertThat(processor(repository, ledger).processNext(Bucket.GUARANTEE)).isTrue();

        verify(repository).markReleased(10, TRANSACTION_ID, NOW);
    }

    @Test
    void concurrentWorkerFindingNoUnlockedRowDoesNothing() {
        var repository = mock(LedgerReleaseRepository.class);
        var ledger = mock(LedgerService.class);
        when(repository.claimNext(Bucket.GUARANTEE, NOW)).thenReturn(Optional.empty());

        assertThat(processor(repository, ledger).processNext(Bucket.GUARANTEE)).isFalse();

        verifyNoInteractions(ledger);
        verify(repository, never()).markReleased(anyLong(), any(), any());
    }

    private static LedgerReleaseProcessor processor(LedgerReleaseRepository repository, LedgerService ledger) {
        return new LedgerReleaseProcessor(repository, ledger, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DueRelease due(Bucket bucket, Origin origin, long amount, String payoutDelay) {
        return new DueRelease(10, ACCOUNT_ID, bucket, amount, origin, SOURCE_AT, RECEIPT_AT, payoutDelay);
    }

    private static LedgerEntry entry(Bucket bucket, Direction direction, long amount, Instant releaseAt, Origin origin) {
        return new LedgerEntry(ACCOUNT_ID, bucket, direction, amount, origin, releaseAt);
    }
}
