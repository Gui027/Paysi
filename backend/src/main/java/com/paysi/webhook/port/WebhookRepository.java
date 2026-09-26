package com.paysi.webhook.port;

import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookDelivery;
import com.paysi.webhook.domain.WebhookEndpoint;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface WebhookRepository {
    void insertEndpoint(WebhookEndpoint endpoint);
    List<WebhookEndpoint> listEndpoints(UUID accountId);
    Optional<WebhookEndpoint> findEndpoint(UUID accountId, UUID endpointId);
    boolean updateEndpoint(UUID accountId, UUID endpointId, String url, Set<String> events, boolean enabled);
    boolean rotateSecret(UUID accountId, UUID endpointId, byte[] encryptedSecret, Instant now);
    void insertOutbox(OutboxEvent event);
    List<OutboxEvent> claimOutbox(Instant now, Instant staleBefore, UUID token, int limit);
    void releaseOutbox(UUID eventId, UUID token);
    void markPublished(UUID eventId, UUID token, Instant now);
    List<WebhookEndpoint> activeEndpoints(UUID accountId, String eventType);
    int nextAttempt(UUID eventId, UUID endpointId);
    void insertDelivery(WebhookDelivery delivery);
    List<RetryClaim> claimRetries(Instant now, Instant staleBefore, UUID token, int limit);
    void finishRetry(UUID deliveryId, UUID token);
    void releaseRetry(UUID deliveryId, UUID token);
    List<WebhookDelivery> deliveryHistory(UUID accountId, int limit);
    Optional<OutboxEvent> findEvent(UUID accountId, UUID eventId);
    void setProfile(UUID accountId, UUID endpointId, String name, UUID productId);
    boolean softDelete(UUID accountId, UUID endpointId);
    void attachDeliveryDetails(UUID deliveryId, String url, String requestBody, String responseBody);
    Optional<UUID> productOfCharge(UUID chargeId);
    boolean productBelongsTo(UUID accountId, UUID productId);

    record RetryClaim(WebhookDelivery delivery, OutboxEvent event, WebhookEndpoint endpoint) { }
}
