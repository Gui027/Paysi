package com.paysi.payment.inbox.port;

import com.paysi.payment.inbox.domain.ProviderEventPayload;

import java.time.Instant;
import java.util.List;

public interface ProviderEventRepository {
    boolean receive(String provider, ProviderEventPayload event, String rawPayload, boolean signatureValid);
    boolean applyEffect(StoredProviderEvent event);
    void markProcessed(String provider, String eventId, Instant now);
    void markIgnored(String provider, String eventId, Instant now);
    void markFailed(String provider, String eventId, String error, int attempt, Instant nextRetryAt);
    List<StoredProviderEvent> lockFailed(Instant now, int limit);

    record StoredProviderEvent(String provider, ProviderEventPayload event, String rawPayload, int attemptCount) {}
}
