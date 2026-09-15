package com.paysi.webhook.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.paysi.core.error.NotFoundException;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.webhook.domain.OutboxEvent;
import com.paysi.webhook.domain.WebhookDelivery;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookRepository;
import com.paysi.webhook.port.WebhookSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookDeliveryService {
    private static final Duration CLAIM_TIMEOUT = Duration.ofMinutes(5);
    private static final List<Duration> RETRIES = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(12));
    private final WebhookRepository repository;
    private final WebhookSender sender;
    private final WebhookSigner signer;
    private final WebhookUrlPolicy urls;
    private final SecretProtector secrets;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public WebhookDeliveryService(WebhookRepository repository, WebhookSender sender, WebhookSigner signer,
                                  WebhookUrlPolicy urls, SecretProtector secrets, ObjectMapper json) {
        this(repository, sender, signer, urls, secrets, json, Clock.systemUTC());
    }
    WebhookDeliveryService(WebhookRepository repository, WebhookSender sender, WebhookSigner signer,
                           WebhookUrlPolicy urls, SecretProtector secrets, ObjectMapper json, Clock clock) {
        this.repository = repository; this.sender = sender; this.signer = signer; this.urls = urls; this.secrets = secrets; this.json = json; this.clock = clock;
    }

    @Transactional
    public int publish(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        Instant now = clock.instant(); UUID token = UUID.randomUUID();
        List<OutboxEvent> events = repository.claimOutbox(now, now.minus(CLAIM_TIMEOUT), token, limit);
        for (OutboxEvent event : events) {
            try {
                for (WebhookEndpoint endpoint : repository.activeEndpoints(event.accountId(), event.type())) deliver(event, endpoint);
                repository.markPublished(event.id(), token, clock.instant());
            } catch (RuntimeException exception) {
                repository.releaseOutbox(event.id(), token);
            }
        }
        return events.size();
    }

    @Transactional
    public int retry(int requestedLimit) {
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        Instant now = clock.instant(); UUID token = UUID.randomUUID();
        var claims = repository.claimRetries(now, now.minus(CLAIM_TIMEOUT), token, limit);
        for (var claim : claims) {
            try {
                if (claim.endpoint().enabled()) deliver(claim.event(), claim.endpoint());
                repository.finishRetry(claim.delivery().id(), token);
            } catch (RuntimeException exception) {
                repository.releaseRetry(claim.delivery().id(), token);
            }
        }
        return claims.size();
    }

    @Transactional
    public void resend(UUID accountId, UUID eventId) {
        OutboxEvent event = repository.findEvent(accountId, eventId).orElseThrow(() -> new NotFoundException("WEBHOOK_EVENT_NOT_FOUND", "Evento de webhook não encontrado"));
        for (WebhookEndpoint endpoint : repository.activeEndpoints(accountId, event.type())) deliver(event, endpoint);
    }

    public List<WebhookDelivery> history(UUID accountId, int limit) { return repository.deliveryHistory(accountId, limit); }

    private void deliver(OutboxEvent event, WebhookEndpoint endpoint) {
        String safeUrl = urls.validate(endpoint.url());
        int attempt = repository.nextAttempt(event.id(), endpoint.id());
        Instant now = clock.instant();
        String body = envelope(event);
        long timestamp = now.getEpochSecond();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Paysi-Event-Id", event.id().toString());
        headers.put("X-Paysi-Timestamp", Long.toString(timestamp));
        headers.put("X-Paysi-Signature", signer.sign(secrets.decrypt(endpoint.encryptedSecret()), timestamp, body));
        if (endpoint.encryptedPreviousSecret() != null && endpoint.secretRotatedAt() != null && now.isBefore(endpoint.secretRotatedAt().plus(Duration.ofHours(24)))) {
            headers.put("X-Paysi-Signature-Previous", signer.sign(secrets.decrypt(endpoint.encryptedPreviousSecret()), timestamp, body));
        }
        WebhookSender.SendResult result = sender.send(safeUrl, body, headers);
        Instant retryAt = result.successful() || attempt > RETRIES.size() ? null : now.plus(RETRIES.get(attempt - 1));
        String error = result.error() == null ? null : result.error().substring(0, Math.min(result.error().length(), 500));
        repository.insertDelivery(new WebhookDelivery(UUID.randomUUID(), event.id(), endpoint.id(), attempt,
                result.statusCode() == 0 ? null : result.statusCode(), error, retryAt, now));
    }

    private String envelope(OutboxEvent event) {
        try {
            ObjectNode root = json.createObjectNode();
            root.put("eventId", event.id().toString()); root.put("type", event.type()); root.put("createdAt", event.createdAt().toString());
            root.set("data", json.readTree(event.payload()));
            return json.writeValueAsString(root);
        } catch (Exception exception) {
            throw new IllegalStateException("Evento persistido possui JSON inválido", exception);
        }
    }
}
