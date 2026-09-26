package com.paysi.sales.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.sales.app.SubscriptionsCsv;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionDetail;
import com.paysi.sales.app.SubscriptionsModels.SubscriptionsPage;
import com.paysi.sales.app.SubscriptionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lista, detalhe e exportação das assinaturas do vendedor. Cancelar continua em POST /v1/subscriptions/{id}/cancel. */
@RestController
@Tag(name = "Assinaturas")
public class SubscriptionsQueryController {
    private static final String COOKIE_NAME = "paysi_session";

    private final SubscriptionsService subscriptions;
    private final SessionService sessions;

    public SubscriptionsQueryController(SubscriptionsService subscriptions, SessionService sessions) {
        this.subscriptions = subscriptions;
        this.sessions = sessions;
    }

    @GetMapping("/v1/subscriptions")
    @Operation(summary = "Listar assinaturas com filtros, resumo e paginação numerada")
    public SubscriptionsPage list(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                  @RequestParam(defaultValue = "active") String tab,
                                  @RequestParam(name = "q", required = false) String query,
                                  @RequestParam(name = "status", required = false) List<String> statuses,
                                  @RequestParam(required = false) String cycle,
                                  @RequestParam(required = false) String method,
                                  @RequestParam(required = false) UUID productId,
                                  @RequestParam(required = false) String from,
                                  @RequestParam(required = false) String to,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        UUID sellerId = accountId(token);
        return subscriptions.list(sellerId,
                subscriptions.filter(tab, query, statuses, cycle, method, productId, from, to), page, size);
    }

    @GetMapping("/v1/subscriptions/export")
    @Operation(summary = "Exportar as assinaturas do filtro em CSV")
    public ResponseEntity<byte[]> export(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                         @RequestParam(defaultValue = "active") String tab,
                                         @RequestParam(name = "q", required = false) String query,
                                         @RequestParam(name = "status", required = false) List<String> statuses,
                                         @RequestParam(required = false) String cycle,
                                         @RequestParam(required = false) String method,
                                         @RequestParam(required = false) UUID productId,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        UUID sellerId = accountId(token);
        var rows = subscriptions.export(sellerId,
                subscriptions.filter(tab, query, statuses, cycle, method, productId, from, to));
        byte[] body = SubscriptionsCsv.build(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("assinaturas-paysi-" + LocalDate.now() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @GetMapping("/v1/subscriptions/{subscriptionId}/details")
    @Operation(summary = "Detalhar assinatura: assinatura, cliente e pagamentos")
    public SubscriptionDetail detail(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                     @PathVariable UUID subscriptionId) {
        return subscriptions.detail(accountId(token), subscriptionId);
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
