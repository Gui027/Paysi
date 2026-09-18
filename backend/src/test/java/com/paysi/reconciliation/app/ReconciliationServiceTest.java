package com.paysi.reconciliation.app;

import com.paysi.core.error.ValidationException;
import com.paysi.reconciliation.port.ReconciliationRepository;
import com.paysi.reconciliation.port.ReconciliationRepository.InternalTotal;
import com.paysi.reconciliation.port.ReconciliationRepository.StatementLine;
import com.paysi.reconciliation.port.ReconciliationRepository.UpsertResult;
import com.paysi.webhook.app.OutboxService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-17T04:00:00Z");
    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();

    @Test
    void zeroDivergenceIsMatchedAndDoesNotAlert() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of(new InternalTotal("ref-1", 10_000, SELLER)));
        when(repository.statementEntries(DAY)).thenReturn(List.of(new StatementLine("ref-1", 10_000, DAY)));
        when(repository.upsert(eq(DAY), eq("ref-1"), eq(10_000L), eq(10_000L), eq(0L), eq("MATCHED"), eq(NOW)))
                .thenReturn(new UpsertResult(ENTRY_ID, null));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var report = service.reconcile(DAY);

        assertThat(report.entries()).hasSize(1);
        assertThat(report.entries().get(0).status()).isEqualTo("MATCHED");
        verifyNoInteractions(outbox);
        verify(repository, never()).markAlerted(any(), any());
    }

    @Test
    void oneCentDifferenceDoesNotAlert() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of(new InternalTotal("ref-1", 10_001, SELLER)));
        when(repository.statementEntries(DAY)).thenReturn(List.of(new StatementLine("ref-1", 10_000, DAY)));
        when(repository.upsert(eq(DAY), eq("ref-1"), eq(10_001L), eq(10_000L), eq(1L), eq("MATCHED"), eq(NOW)))
                .thenReturn(new UpsertResult(ENTRY_ID, null));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var report = service.reconcile(DAY);

        assertThat(report.entries().get(0).status()).isEqualTo("MATCHED");
        assertThat(report.entries().get(0).differenceCents()).isEqualTo(1);
        verifyNoInteractions(outbox);
    }

    @Test
    void twoCentDifferenceAlertsExactlyOnce() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of(new InternalTotal("ref-1", 10_002, SELLER)));
        when(repository.statementEntries(DAY)).thenReturn(List.of(new StatementLine("ref-1", 10_000, DAY)));
        when(repository.upsert(eq(DAY), eq("ref-1"), eq(10_002L), eq(10_000L), eq(2L), eq("DIVERGED"), eq(NOW)))
                .thenReturn(new UpsertResult(ENTRY_ID, null));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var report = service.reconcile(DAY);

        assertThat(report.entries().get(0).status()).isEqualTo("DIVERGED");
        assertThat(report.entries().get(0).differenceCents()).isEqualTo(2);
        verify(outbox).append(eq(SELLER), eq("reconciliation.diverged"), any());
        verify(repository).markAlerted(ENTRY_ID, NOW);
    }

    @Test
    void rerunningSameDayDoesNotDuplicateAlert() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of(new InternalTotal("ref-1", 10_100, SELLER)));
        when(repository.statementEntries(DAY)).thenReturn(List.of(new StatementLine("ref-1", 10_000, DAY)));
        // Segunda execução: alerted_at já veio preenchido do upsert anterior.
        when(repository.upsert(eq(DAY), eq("ref-1"), eq(10_100L), eq(10_000L), eq(100L), eq("DIVERGED"), eq(NOW)))
                .thenReturn(new UpsertResult(ENTRY_ID, NOW.minusSeconds(3600)));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.reconcile(DAY);

        verifyNoInteractions(outbox);
        verify(repository, never()).markAlerted(any(), any());
    }

    @Test
    void missingProviderEntryStillReconcilesWithZeroProviderCents() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of(new InternalTotal("ref-only-internal", 5_000, SELLER)));
        when(repository.statementEntries(DAY)).thenReturn(List.of());
        when(repository.upsert(eq(DAY), eq("ref-only-internal"), eq(5_000L), eq(0L), eq(5_000L), eq("DIVERGED"),
                eq(NOW))).thenReturn(new UpsertResult(ENTRY_ID, null));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        var report = service.reconcile(DAY);

        assertThat(report.entries().get(0).providerCents()).isZero();
        assertThat(report.entries().get(0).status()).isEqualTo("DIVERGED");
        verify(outbox).append(eq(SELLER), eq("reconciliation.diverged"), any());
    }

    @Test
    void missingInternalEntryAlertsUsingPlatformAccount() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        when(repository.internalTotals(DAY)).thenReturn(List.of());
        when(repository.statementEntries(DAY)).thenReturn(List.of(new StatementLine("ref-only-provider", 7_000, DAY)));
        when(repository.upsert(eq(DAY), eq("ref-only-provider"), eq(0L), eq(7_000L), eq(-7_000L), eq("DIVERGED"),
                eq(NOW))).thenReturn(new UpsertResult(ENTRY_ID, null));
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.reconcile(DAY);

        verify(outbox).append(eq(UUID.fromString("00000000-0000-0000-0000-0000000000b1")),
                eq("reconciliation.diverged"), any());
    }

    @Test
    void reconcileRequiresDate() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.reconcile(null)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(repository, outbox);
    }

    @Test
    void importStatementRejectsEmptyList() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.importStatement(List.of())).isInstanceOf(ValidationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void importStatementRejectsNonPositiveAmount() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.importStatement(List.of(new ImportStatementLine("ref-1", 0, DAY))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void importStatementDelegatesValidLinesToRepository() {
        var repository = mock(ReconciliationRepository.class);
        var outbox = mock(OutboxService.class);
        var service = new ReconciliationService(repository, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

        service.importStatement(List.of(new ImportStatementLine("ref-1", 1_000, DAY)));

        verify(repository).importStatementLines(anyList(), eq(NOW));
    }
}
