package com.paysi.observability.alert.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.observability.alert.domain.AlertEvent;
import com.paysi.observability.alert.port.AlertChannel;
import com.paysi.observability.alert.port.AlertRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertServiceTest {

    @Test
    void persistsEvidenceBeforeCallingTheChannel() {
        var repository = mock(AlertRepository.class);
        var channel = mock(AlertChannel.class);
        var meters = new SimpleMeterRegistry();
        var clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC);
        var service = new AlertService(repository, channel, new ObjectMapper(), meters, clock);

        service.raise("LEDGER_INTEGRITY_VIOLATION", "CRITICAL",
                Map.of("view", "v_check_negative_user_buckets", "rows", 3));

        var inOrder = inOrder(repository, channel);
        inOrder.verify(repository).insert(any(AlertEvent.class));
        inOrder.verify(channel).send(any(AlertEvent.class));
        assertThat(meters.counter("paysi.alerts.raised", "type", "LEDGER_INTEGRITY_VIOLATION",
                "severity", "CRITICAL").count()).isEqualTo(1.0);
    }

    @Test
    void channelFailureDoesNotPreventEvidenceFromBeingPersisted() {
        var repository = mock(AlertRepository.class);
        var channel = mock(AlertChannel.class);
        doThrow(new RuntimeException("Slack indisponível")).when(channel).send(any());
        var service = new AlertService(repository, channel, new ObjectMapper(), new SimpleMeterRegistry());

        // A falha do canal não é responsabilidade do AlertService engolir aqui —
        // é responsabilidade do adaptador de canal (SlackAlertChannel já trata
        // RestClientException). Este teste documenta que a evidência já foi
        // gravada antes da chamada ao canal, então mesmo se o canal lançar,
        // o registro em ops_alerts é fato consumado.
        try {
            service.raise("LEDGER_INTEGRITY_VIOLATION", "CRITICAL", Map.of("view", "x"));
        } catch (RuntimeException ignored) {
            // esperado neste teste — canal simulado propaga
        }

        verify(repository).insert(any(AlertEvent.class));
    }
}
