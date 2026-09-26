package com.paysi.webhook.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.webhook.app.WebhookDeliveryService;
import com.paysi.webhook.app.WebhookDeliveryService.TestResult;
import com.paysi.webhook.app.WebhookEndpointService;
import com.paysi.webhook.app.WebhookPanelModels.BulkResendResult;
import com.paysi.webhook.app.WebhookPanelModels.EndpointItem;
import com.paysi.webhook.app.WebhookPanelModels.LogDetail;
import com.paysi.webhook.app.WebhookPanelModels.LogsPage;
import com.paysi.webhook.app.WebhookPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Tela Apps → Webhooks: endpoints com nome e produto, envio de teste e logs com reenvio. */
@RestController
@Tag(name = "Webhooks (painel)")
public class WebhookPanelController {
    private static final String COOKIE_NAME = "paysi_session";

    public record WebhookRequest(String name, UUID productId, String url, Set<String> events) { }

    public record TestRequest(String url, UUID endpointId) { }

    public record ResendRequest(List<UUID> eventIds) { }

    private final WebhookEndpointService endpoints;
    private final WebhookDeliveryService deliveries;
    private final WebhookPanelService panel;
    private final SessionService sessions;

    public WebhookPanelController(WebhookEndpointService endpoints, WebhookDeliveryService deliveries, WebhookPanelService panel,
                                  SessionService sessions) {
        this.endpoints = endpoints;
        this.deliveries = deliveries;
        this.panel = panel;
        this.sessions = sessions;
    }

    @GetMapping("/v1/webhooks")
    @Operation(summary = "Listar webhooks, com busca e filtro por produto")
    public List<EndpointItem> list(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                   @RequestParam(name = "q", required = false) String query,
                                   @RequestParam(required = false) UUID productId) {
        return panel.endpoints(owner(token), query, productId);
    }

    @GetMapping("/v1/webhooks/{id}")
    @Operation(summary = "Detalhar um webhook")
    public WebhookEndpointService.EndpointView get(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        return endpoints.get(owner(token), id);
    }

    @PostMapping("/v1/webhooks")
    @Operation(summary = "Criar um webhook; o segredo de assinatura aparece só nesta resposta")
    public ResponseEntity<WebhookEndpointService.CreatedEndpoint> create(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                                                          @RequestBody WebhookRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(endpoints.create(owner(token), request.name(), request.productId(), request.url(), request.events(), true));
    }

    @PutMapping("/v1/webhooks/{id}")
    @Operation(summary = "Editar um webhook")
    public WebhookEndpointService.EndpointView update(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                                      @PathVariable UUID id, @RequestBody WebhookRequest request) {
        return endpoints.update(owner(token), id, request.name(), request.productId(), request.url(), request.events(), true);
    }

    @DeleteMapping("/v1/webhooks/{id}")
    @Operation(summary = "Excluir um webhook (os logs deixam de aparecer e nada mais é enviado)")
    public ResponseEntity<Void> delete(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        endpoints.delete(owner(token), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/webhooks/{id}/rotate-secret")
    @Operation(summary = "Gerar um novo segredo de assinatura (o anterior vale por mais 24 horas)")
    public WebhookEndpointService.RotatedSecret rotate(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        return endpoints.rotate(owner(token), id);
    }

    @PostMapping("/v1/webhooks/test")
    @Operation(summary = "Enviar um evento de teste para a URL")
    public TestResult test(@CookieValue(name = COOKIE_NAME, required = false) String token, @RequestBody TestRequest request) {
        return deliveries.test(owner(token), request.url(), request.endpointId());
    }

    @GetMapping("/v1/webhooks/{id}/logs")
    @Operation(summary = "Logs de envio de um webhook, com filtros e paginação")
    public LogsPage logs(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id,
                         @RequestParam(name = "event", required = false) List<String> events,
                         @RequestParam(name = "q", required = false) String query,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) Integer page) {
        UUID owner = owner(token);
        endpoints.get(owner, id);
        return panel.logs(owner, id, panel.filter(events, query, from, to), page);
    }

    @GetMapping("/v1/webhooks/{id}/logs/{eventId}")
    @Operation(summary = "Detalhes de um envio: requisição e resposta")
    public LogDetail logDetail(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id, @PathVariable UUID eventId) {
        return panel.detail(owner(token), id, eventId);
    }

    @PostMapping("/v1/webhooks/{id}/logs/{eventId}/resend")
    @Operation(summary = "Reenviar um evento para este webhook")
    public ResponseEntity<Void> resend(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id, @PathVariable UUID eventId) {
        deliveries.resendToEndpoint(owner(token), id, eventId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/v1/webhooks/{id}/logs/resend")
    @Operation(summary = "Reenviar vários eventos para este webhook (até 50)")
    public BulkResendResult resendMany(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id,
                                       @RequestBody ResendRequest request) {
        return new BulkResendResult(deliveries.resendManyToEndpoint(owner(token), id, request.eventIds()));
    }

    private UUID owner(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
