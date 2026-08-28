package com.paysi.payment.inbox.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.inbox.domain.*;
import com.paysi.payment.inbox.port.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Service
public class ProviderEventService {
    private static final Duration[] RETRY_DELAYS = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
            Duration.ofHours(2), Duration.ofHours(12)
    };

    private final ObjectMapper json;
    private final PaymentEventSignatureVerifier signatures;
    private final ProviderEventRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ProviderEventService(ObjectMapper json, PaymentEventSignatureVerifier signatures,
                                ProviderEventRepository repository) {
        this(json, signatures, repository, Clock.systemUTC());
    }

    ProviderEventService(ObjectMapper json, PaymentEventSignatureVerifier signatures,
                         ProviderEventRepository repository, Clock clock) {
        this.json = json;
        this.signatures = signatures;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public ProviderEventResult handle(String provider, String rawPayload, String signature) {
        var event = parse(rawPayload);
        boolean valid = signatures.valid(provider, rawPayload, signature);
        if (!repository.receive(provider, event, rawPayload, valid)) {
            return new ProviderEventResult("DUPLICATE", true);
        }
        if (!valid) return new ProviderEventResult("IGNORED", false);
        return process(new ProviderEventRepository.StoredProviderEvent(provider, event, rawPayload, 0));
    }

    @Transactional
    public int retryFailed(int limit) {
        int processed = 0;
        for (var event : repository.lockFailed(clock.instant(), limit)) {
            if (process(event).status().equals("PROCESSED")) processed++;
        }
        return processed;
    }

    private ProviderEventResult process(ProviderEventRepository.StoredProviderEvent stored) {
        try {
            boolean applied = repository.applyEffect(stored);
            if (applied) {
                repository.markProcessed(stored.provider(), stored.event().providerEventId(), clock.instant());
                return new ProviderEventResult("PROCESSED", false);
            }
            repository.markIgnored(stored.provider(), stored.event().providerEventId(), clock.instant());
            return new ProviderEventResult("IGNORED", false);
        } catch (RuntimeException exception) {
            int attempt = stored.attemptCount() + 1;
            Duration delay = RETRY_DELAYS[Math.min(attempt - 1, RETRY_DELAYS.length - 1)];
            repository.markFailed(stored.provider(), stored.event().providerEventId(),
                    exception.getClass().getSimpleName(), attempt, clock.instant().plus(delay));
            return new ProviderEventResult("FAILED", false);
        }
    }

    private ProviderEventPayload parse(String rawPayload) {
        try {
            return json.readValue(rawPayload, ProviderEventPayload.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ValidationException("PROVIDER_EVENT_INVALID", "Evento do provedor inválido", "payload");
        }
    }
}
