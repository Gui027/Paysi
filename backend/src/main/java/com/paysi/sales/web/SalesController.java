package com.paysi.sales.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.sales.app.RefundsCsv;
import com.paysi.sales.app.SalesCsv;
import com.paysi.sales.app.SalesModels.RefundsPage;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SalesPage;
import com.paysi.sales.app.SalesService;
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

/** Vendas e reembolsos do vendedor logado. Uma venda é uma cobrança (cada ciclo de assinatura é uma venda). */
@RestController
@Tag(name = "Vendas")
public class SalesController {
    private static final String COOKIE_NAME = "paysi_session";

    private final SalesService sales;
    private final SessionService sessions;

    public SalesController(SalesService sales, SessionService sessions) {
        this.sales = sales;
        this.sessions = sessions;
    }

    @GetMapping("/v1/sales")
    @Operation(summary = "Listar vendas com filtros, resumo e paginação numerada")
    public SalesPage list(@CookieValue(name = COOKIE_NAME, required = false) String token,
                          @RequestParam(defaultValue = "approved") String tab,
                          @RequestParam(name = "q", required = false) String query,
                          @RequestParam(name = "status", required = false) List<String> statuses,
                          @RequestParam(required = false) String method,
                          @RequestParam(required = false) UUID productId,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size) {
        UUID sellerId = accountId(token);
        return sales.list(sellerId, sales.filter(tab, query, statuses, method, productId, from, to), page, size);
    }

    @GetMapping("/v1/sales/export")
    @Operation(summary = "Exportar as vendas do filtro em CSV")
    public ResponseEntity<byte[]> export(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                         @RequestParam(defaultValue = "approved") String tab,
                                         @RequestParam(name = "q", required = false) String query,
                                         @RequestParam(name = "status", required = false) List<String> statuses,
                                         @RequestParam(required = false) String method,
                                         @RequestParam(required = false) UUID productId,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        UUID sellerId = accountId(token);
        var rows = sales.export(sellerId, sales.filter(tab, query, statuses, method, productId, from, to));
        byte[] body = SalesCsv.build(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("vendas-paysi-" + LocalDate.now() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @GetMapping("/v1/sales/{chargeId}")
    @Operation(summary = "Detalhar uma venda: venda, cliente e valores")
    public SaleDetail detail(@CookieValue(name = COOKIE_NAME, required = false) String token,
                             @PathVariable UUID chargeId) {
        return sales.detail(accountId(token), chargeId);
    }

    @GetMapping("/v1/refunds")
    @Operation(summary = "Listar reembolsos das vendas do vendedor")
    public RefundsPage refunds(@CookieValue(name = COOKIE_NAME, required = false) String token,
                               @RequestParam(name = "q", required = false) String query,
                               @RequestParam(name = "status", required = false) List<String> statuses,
                               @RequestParam(name = "origin", required = false) List<String> origins,
                               @RequestParam(required = false) String from,
                               @RequestParam(required = false) String to,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return sales.refunds(accountId(token), sales.refundFilter(query, statuses, origins, from, to), page, size);
    }

    @GetMapping("/v1/refunds/export")
    @Operation(summary = "Exportar os reembolsos do filtro em CSV")
    public ResponseEntity<byte[]> exportRefunds(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                                @RequestParam(name = "q", required = false) String query,
                                                @RequestParam(name = "status", required = false) List<String> statuses,
                                                @RequestParam(name = "origin", required = false) List<String> origins,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        var rows = sales.exportRefunds(accountId(token), sales.refundFilter(query, statuses, origins, from, to));
        byte[] body = RefundsCsv.build(rows).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("reembolsos-paysi-" + LocalDate.now() + ".csv").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
