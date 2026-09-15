package com.paysi.webhook.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.port.WebhookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class OutboxService {
    private final WebhookRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public OutboxService(WebhookRepository repository, ObjectMapper json) { this(repository, json, Clock.systemUTC()); }
    OutboxService(WebhookRepository repository, ObjectMapper json, Clock clock) { this.repository = repository; this.json = json; this.clock = clock; }

    @Transactional
    public UUID append(UUID accountId, String eventType, Object payload) {
        try {
            UUID id = UUID.randomUUID();
            repository.insertOutbox(new OutboxEvent(id, accountId, eventType, json.writeValueAsString(payload), clock.instant()));
            return id;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload de evento inválido", exception);
        }
    }
}
