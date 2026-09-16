package com.paysi.subscription.web;

import com.paysi.core.error.ValidationException;
import com.paysi.identity.session.app.SessionService;
import com.paysi.subscription.app.SubscriptionService;
import com.paysi.subscription.web.dto.SubscriptionCreateRequest;
import com.paysi.subscription.web.dto.SubscriptionDetailResponse;
import com.paysi.subscription.web.dto.SubscriptionPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@Tag(name = "Assinaturas")
public class SubscriptionController {
    private static final String COOKIE_NAME = "paysi_session";

    private final SubscriptionService subscriptions;
    private final SessionService sessions;

    public SubscriptionController(SubscriptionService subscriptions, SessionService sessions) {
        this.subscriptions = subscriptions;
        this.sessions = sessions;
    }

    @PostMapping("/v1/offers/{slug}/subscriptions")
    @Operation(summary = "Criar assinatura a partir do checkout público")
    public ResponseEntity<Void> create(
            @PathVariable String slug,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SubscriptionCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED", "Header Idempotency-Key é obrigatório", null);
        }
        var result = subscriptions.create(request.toCommand(slug, idempotencyKey));
        var status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/v1/subscriptions/" + result.subscriptionId()))
                .build();
    }

    @GetMapping("/v1/accounts/me/subscriptions")
    @Operation(summary = "Listar assinaturas do vendedor")
    public SubscriptionPageResponse list(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return SubscriptionPageResponse.from(subscriptions.list(accountId(token), cursor, limit));
    }

    @GetMapping("/v1/subscriptions/{subscriptionId}")
    @Operation(summary = "Detalhar assinatura")
    public SubscriptionDetailResponse detail(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID subscriptionId) {
        return SubscriptionDetailResponse.from(subscriptions.detail(accountId(token), subscriptionId));
    }

    @PostMapping("/v1/subscriptions/{subscriptionId}/cancel")
    @Operation(summary = "Cancelar assinatura ao fim do período vigente")
    public ResponseEntity<Void> cancel(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID subscriptionId) {
        subscriptions.cancelAtPeriodEnd(accountId(token), subscriptionId);
        return ResponseEntity.noContent().build();
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
