package com.paysi.webhook.app;

import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.security.mfa.port.SecretProtector;
import com.paysi.webhook.domain.WebhookEndpoint;
import com.paysi.webhook.port.WebhookRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WebhookEndpointService {
    private static final Pattern EVENT = Pattern.compile("[A-Z][A-Z0-9_.-]{2,63}");
    private final WebhookRepository repository;
    private final WebhookUrlPolicy urls;
    private final SecretProtector secrets;
    private final SecureRandom random;
    private final Clock clock;

    @Autowired
    public WebhookEndpointService(WebhookRepository repository, WebhookUrlPolicy urls, SecretProtector secrets) {
        this(repository, urls, secrets, new SecureRandom(), Clock.systemUTC());
    }

    WebhookEndpointService(WebhookRepository repository, WebhookUrlPolicy urls, SecretProtector secrets,
                           SecureRandom random, Clock clock) {
        this.repository = repository; this.urls = urls; this.secrets = secrets; this.random = random; this.clock = clock;
    }

    @Transactional
    public CreatedEndpoint create(UUID accountId, String url, Set<String> events, boolean enabled) {
        Set<String> normalized = events(events);
        byte[] clear = secret();
        var endpoint = new WebhookEndpoint(UUID.randomUUID(), accountId, urls.validate(url), normalized, enabled,
                secrets.encrypt(clear), null, null, clock.instant());
        repository.insertEndpoint(endpoint);
        return new CreatedEndpoint(publicView(endpoint), encode(clear));
    }

    /** Criação pelo painel: com nome e, opcionalmente, restrita a um produto do vendedor. */
    @Transactional
    public CreatedEndpoint create(UUID accountId, String name, UUID productId, String url, Set<String> events, boolean enabled) {
        String cleanName = name(name);
        checkProduct(accountId, productId);
        CreatedEndpoint created = create(accountId, url, events, enabled);
        repository.setProfile(accountId, created.endpoint().id(), cleanName, productId);
        return new CreatedEndpoint(repository.findEndpoint(accountId, created.endpoint().id()).map(WebhookEndpointService::publicView).orElseThrow(WebhookEndpointService::missing), created.secret());
    }

    @Transactional
    public EndpointView update(UUID accountId, UUID endpointId, String name, UUID productId, String url, Set<String> events, boolean enabled) {
        String cleanName = name(name);
        checkProduct(accountId, productId);
        update(accountId, endpointId, url, events, enabled);
        repository.setProfile(accountId, endpointId, cleanName, productId);
        return repository.findEndpoint(accountId, endpointId).map(WebhookEndpointService::publicView).orElseThrow(WebhookEndpointService::missing);
    }

    @Transactional
    public void delete(UUID accountId, UUID endpointId) {
        if (!repository.softDelete(accountId, endpointId)) throw missing();
    }

    public EndpointView get(UUID accountId, UUID endpointId) {
        return repository.findEndpoint(accountId, endpointId).map(WebhookEndpointService::publicView).orElseThrow(WebhookEndpointService::missing);
    }

    private void checkProduct(UUID accountId, UUID productId) {
        if (productId != null && !repository.productBelongsTo(accountId, productId)) {
            throw new ValidationException("WEBHOOK_PRODUCT_INVALID", "Produto inválido", "productId");
        }
    }

    private static String name(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 60) throw new ValidationException("WEBHOOK_NAME_INVALID", "Informe um nome de até 60 caracteres", "name");
        return name;
    }

    public List<EndpointView> list(UUID accountId) {
        return repository.listEndpoints(accountId).stream().map(WebhookEndpointService::publicView).toList();
    }

    @Transactional
    public EndpointView update(UUID accountId, UUID endpointId, String url, Set<String> events, boolean enabled) {
        String safeUrl = urls.validate(url);
        Set<String> normalized = events(events);
        if (!repository.updateEndpoint(accountId, endpointId, safeUrl, normalized, enabled)) throw missing();
        return repository.findEndpoint(accountId, endpointId).map(WebhookEndpointService::publicView).orElseThrow(WebhookEndpointService::missing);
    }

    @Transactional
    public RotatedSecret rotate(UUID accountId, UUID endpointId) {
        byte[] clear = secret();
        var now = clock.instant();
        if (!repository.rotateSecret(accountId, endpointId, secrets.encrypt(clear), now)) throw missing();
        return new RotatedSecret(endpointId, encode(clear), now.plusSeconds(86_400));
    }

    private byte[] secret() { byte[] value = new byte[32]; random.nextBytes(value); return value; }
    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static Set<String> events(Set<String> values) {
        if (values == null || values.isEmpty() || values.size() > 32) throw new ValidationException("WEBHOOK_EVENTS_INVALID", "Selecione de 1 a 32 eventos", "events");
        var normalized = new LinkedHashSet<String>();
        values.forEach(value -> { String item = value == null ? "" : value.strip().toUpperCase(); if (!EVENT.matcher(item).matches()) throw new ValidationException("WEBHOOK_EVENT_INVALID", "Tipo de evento inválido", "events"); normalized.add(item); });
        return Set.copyOf(normalized);
    }
    private static EndpointView publicView(WebhookEndpoint endpoint) { return new EndpointView(endpoint.id(), endpoint.url(), endpoint.events(), endpoint.enabled(), endpoint.secretRotatedAt(), endpoint.createdAt(), endpoint.name(), endpoint.productId()); }
    private static NotFoundException missing() { return new NotFoundException("WEBHOOK_ENDPOINT_NOT_FOUND", "Endpoint de webhook não encontrado"); }

    public record EndpointView(UUID id, String url, Set<String> events, boolean enabled,
                               java.time.Instant secretRotatedAt, java.time.Instant createdAt, String name, UUID productId) {
        public EndpointView(UUID id, String url, Set<String> events, boolean enabled, java.time.Instant secretRotatedAt, java.time.Instant createdAt) {
            this(id, url, events, enabled, secretRotatedAt, createdAt, null, null);
        }
    }
    public record CreatedEndpoint(EndpointView endpoint, String secret) { }
    public record RotatedSecret(UUID endpointId, String secret, java.time.Instant previousSecretValidUntil) { }
}
