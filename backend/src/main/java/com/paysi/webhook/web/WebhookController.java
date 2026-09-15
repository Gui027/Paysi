package com.paysi.webhook.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.webhook.app.WebhookDeliveryService;
import com.paysi.webhook.app.WebhookEndpointService;
import com.paysi.webhook.domain.WebhookDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts/me/webhooks")
public class WebhookController {
    private static final String SESSION_COOKIE = "paysi_session";
    private final WebhookEndpointService endpoints;
    private final WebhookDeliveryService deliveries;
    private final SessionService sessions;

    public WebhookController(WebhookEndpointService endpoints, WebhookDeliveryService deliveries, SessionService sessions) {
        this.endpoints = endpoints; this.deliveries = deliveries; this.sessions = sessions;
    }

    @PostMapping
    public ResponseEntity<WebhookEndpointService.CreatedEndpoint> create(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                                                          @Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpoints.create(accountId(token), request.url(), request.events(), request.enabled()));
    }

    @GetMapping public List<WebhookEndpointService.EndpointView> list(@CookieValue(name = SESSION_COOKIE, required = false) String token) { return endpoints.list(accountId(token)); }

    @PutMapping("/{endpointId}")
    public WebhookEndpointService.EndpointView update(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                                       @PathVariable UUID endpointId, @Valid @RequestBody EndpointRequest request) {
        return endpoints.update(accountId(token), endpointId, request.url(), request.events(), request.enabled());
    }

    @PostMapping("/{endpointId}/rotate-secret")
    public WebhookEndpointService.RotatedSecret rotate(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                                        @PathVariable UUID endpointId) { return endpoints.rotate(accountId(token), endpointId); }

    @GetMapping("/deliveries")
    public List<WebhookDelivery> history(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                         @RequestParam(defaultValue = "50") int limit) { return deliveries.history(accountId(token), limit); }

    @PostMapping("/deliveries/{eventId}/resend")
    public ResponseEntity<Void> resend(@CookieValue(name = SESSION_COOKIE, required = false) String token,
                                       @PathVariable UUID eventId) { deliveries.resend(accountId(token), eventId); return ResponseEntity.accepted().build(); }

    private UUID accountId(String token) { return sessions.authenticate(token).session().accountId(); }
    public record EndpointRequest(@NotBlank String url, @NotEmpty Set<String> events, boolean enabled) { }
}
