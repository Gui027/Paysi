package com.paysi.payment.inbox.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.ledger.app.SaleLedgerService;
import com.paysi.payment.inbox.adapter.DispatchingProviderEventNormalizer;
import com.paysi.payment.inbox.domain.ProviderEventPayload;
import com.paysi.payment.inbox.port.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProviderEventServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
    private static final String RAW = """
            {"providerEventId":"evt-1","eventType":"PAYMENT_CONFIRMED",
             "chargeId":"11111111-1111-1111-1111-111111111111",
             "providerChargeId":"charge-1","occurredAt":"2026-08-28T11:59:00Z"}
            """;

    @Test
    void simultaneousDeliveryAppliesEffectExactlyOnce() throws Exception {
        var repository = mock(ProviderEventRepository.class);
        var signatures = mock(PaymentEventSignatureVerifier.class);
        when(signatures.valid("fake", RAW, "signature")).thenReturn(true);
        var first = new AtomicBoolean(true);
        when(repository.receive(eq("fake"), any(), any(), eq(true)))
                .thenAnswer(call -> first.compareAndSet(true, false));
        when(repository.applyEffect(any())).thenReturn(true);
        var saleLedger = mock(SaleLedgerService.class);
        var service = service(signatures, repository, saleLedger);

        try (var executor = Executors.newFixedThreadPool(8)) {
            var calls = java.util.stream.IntStream.range(0, 40)
                    .mapToObj(index -> (Callable<String>) () ->
                            service.handle("fake", RAW, "signature").status()).toList();
            var statuses = executor.invokeAll(calls).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }).toList();
            assertThat(statuses).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.Stream.concat(java.util.stream.Stream.of("PROCESSED"),
                            java.util.stream.Stream.generate(() -> "DUPLICATE").limit(39)).toList());
        }
        verify(repository, times(1)).applyEffect(any());
        verify(repository, times(1)).markProcessed("fake", "evt-1", NOW);
        verify(saleLedger, times(1)).creditForCharge(
                java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                Instant.parse("2026-08-28T11:59:00Z"));
    }

    @Test
    void invalidSignatureIsRecordedAndNeverChangesCharge() {
        var repository = mock(ProviderEventRepository.class);
        var signatures = mock(PaymentEventSignatureVerifier.class);
        when(signatures.valid(any(), any(), any())).thenReturn(false);
        when(repository.receive(eq("fake"), any(), any(), eq(false))).thenReturn(true);

        assertThat(service(signatures, repository, mock(SaleLedgerService.class)).handle("fake", RAW, "invalid").status())
                .isEqualTo("IGNORED");
        verify(repository).receive(eq("fake"), any(), any(), eq(false));
        verify(repository, never()).applyEffect(any());
    }

    @Test
    void failedEventIsScheduledAndReprocessed() throws Exception {
        var repository = mock(ProviderEventRepository.class);
        var signatures = mock(PaymentEventSignatureVerifier.class);
        when(signatures.valid(any(), any(), any())).thenReturn(true);
        when(repository.receive(any(), any(), any(), eq(true))).thenReturn(true);
        when(repository.applyEffect(any())).thenThrow(new IllegalStateException()).thenReturn(true);
        var service = service(signatures, repository, mock(SaleLedgerService.class));

        assertThat(service.handle("fake", RAW, "signature").status()).isEqualTo("FAILED");
        verify(repository).markFailed("fake", "evt-1", "IllegalStateException", 1,
                NOW.plus(Duration.ofMinutes(1)));

        var event = new ProviderEventRepository.StoredProviderEvent("fake",
                new ObjectMapper().findAndRegisterModules().readValue(RAW, ProviderEventPayload.class), RAW, 1);
        when(repository.lockFailed(NOW, 100)).thenReturn(List.of(event));
        assertThat(service.retryFailed(100)).isEqualTo(1);
        verify(repository).markProcessed("fake", "evt-1", NOW);
    }

    private static ProviderEventService service(PaymentEventSignatureVerifier signatures,
                                                 ProviderEventRepository repository, SaleLedgerService saleLedger) {
        var json = new ObjectMapper().findAndRegisterModules();
        return new ProviderEventService(json, signatures, new DispatchingProviderEventNormalizer(json, providerId -> java.util.Optional.empty()),
                repository, saleLedger, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
