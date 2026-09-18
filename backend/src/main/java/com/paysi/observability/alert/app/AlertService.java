package com.paysi.observability.alert.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.observability.alert.domain.AlertEvent;
import com.paysi.observability.alert.port.AlertChannel;
import com.paysi.observability.alert.port.AlertRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Ponto único de disparo de alerta operacional. Sempre grava a evidência em
 * {@code ops_alerts} antes de tentar o canal externo (documento 3, §5.1): a
 * prova de que o alerta existiu não pode depender de o Slack estar no ar.
 *
 * <p>Não é transacional com o chamador de propósito — grava a alerta committed
 * mesmo quando quem chamou está dentro de uma transação que ainda vai decidir
 * se comita, porque um alerta sobre uma condição que já foi observada (ex.: uma
 * view de integridade não vazia) não deve desaparecer se algo mais tarde na
 * mesma unidade de trabalho for revertido.
 */
@Service
public class AlertService {
    private final AlertRepository repository;
    private final AlertChannel channel;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final Clock clock;

    @Autowired
    public AlertService(AlertRepository repository, AlertChannel channel, ObjectMapper json,
            MeterRegistry meters) {
        this(repository, channel, json, meters, Clock.systemUTC());
    }

    AlertService(AlertRepository repository, AlertChannel channel, ObjectMapper json,
            MeterRegistry meters, Clock clock) {
        this.repository = repository;
        this.channel = channel;
        this.json = json;
        this.meters = meters;
        this.clock = clock;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void raise(String type, String severity, Object payload) {
        AlertEvent event = new AlertEvent(UUID.randomUUID(), type, severity, toJson(payload), clock.instant());
        repository.insert(event);
        Counter.builder("paysi.alerts.raised")
                .tag("type", type)
                .tag("severity", severity)
                .register(meters)
                .increment();
        channel.send(event);
    }

    private String toJson(Object payload) {
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Payload de alerta inválido", error);
        }
    }
}
