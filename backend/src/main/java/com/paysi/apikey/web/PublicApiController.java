package com.paysi.apikey.web;

import com.paysi.affiliate.app.AffiliationRole;
import com.paysi.affiliate.app.AffiliationService;
import com.paysi.affiliate.web.dto.AffiliationPageResponse;
import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.apikey.app.ApiKeyModels.AccessToken;
import com.paysi.apikey.app.ApiKeyModels.ApiPrincipal;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.catalog.product.web.dto.ProductPageResponse;
import com.paysi.catalog.product.web.dto.ProductResponse;
import com.paysi.checkout.refund.app.RefundCommand;
import com.paysi.checkout.refund.app.RefundService;
import com.paysi.finance.app.FinanceModels.Overview;
import com.paysi.finance.app.FinanceModels.PayoutsPage;
import com.paysi.finance.app.FinanceService;
import com.paysi.reports.app.ReportsModels.Report;
import com.paysi.reports.app.ReportsService;
import com.paysi.sales.app.SalesModels.SaleDetail;
import com.paysi.sales.app.SalesModels.SalesPage;
import com.paysi.sales.app.SalesService;
import com.paysi.webhook.app.WebhookEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API pública da Paysi para integrações: autenticada por token Bearer (obtido com client_id + client_secret) e
 * limitada aos endpoints que o vendedor liberou na API Key. Reaproveita os mesmos serviços do painel.
 */
@RestController
@Tag(name = "API pública")
public class PublicApiController {
    private static final String ACCOUNT = "X-Paysi-Account-Id";

    public record TokenRequest(String client_id, String client_secret) { }

    public record RefundRequest(Long amountCents, String reason) { }

    public record RefundOut(UUID refundId, String status, long sellerCents, long affiliateCents, long platformCents,
                            long providerCents, long chargeRefundedCents, String chargeStatus) { }

    public record WebhookRequest(String url, Set<String> events, boolean enabled) { }

    public record Me(UUID accountId, List<String> scopes) { }

    private final ApiAuthService auth;
    private final ProductService products;
    private final SalesService sales;
    private final RefundService refunds;
    private final AffiliationService affiliations;
    private final FinanceService finance;
    private final ReportsService reports;
    private final WebhookEndpointService webhooks;

    public PublicApiController(ApiAuthService auth, ProductService products, SalesService sales, RefundService refunds,
                               AffiliationService affiliations, FinanceService finance, ReportsService reports,
                               WebhookEndpointService webhooks) {
        this.auth = auth;
        this.products = products;
        this.sales = sales;
        this.refunds = refunds;
        this.affiliations = affiliations;
        this.finance = finance;
        this.reports = reports;
        this.webhooks = webhooks;
    }

    @PostMapping("/v1/public/oauth/token")
    @Operation(summary = "Trocar client_id e client_secret por um token de acesso (válido por 1 hora)")
    public AccessToken token(@RequestBody TokenRequest request) {
        return auth.issue(request.client_id(), request.client_secret());
    }

    @GetMapping("/v1/public/me")
    @Operation(summary = "Conferir a conta e os endpoints liberados para o token")
    public Me me(@RequestHeader(name = "Authorization", required = false) String authorization,
                 @RequestHeader(name = ACCOUNT, required = false) String account) {
        ApiPrincipal principal = auth.authenticate(authorization, account, null);
        return new Me(principal.accountId(), principal.scopes());
    }

    // ---------- Produtos ----------

    @GetMapping("/v1/public/products")
    @Operation(summary = "Listar produtos por cursor")
    public ProductPageResponse products(@RequestHeader(name = "Authorization", required = false) String authorization,
                                        @RequestHeader(name = ACCOUNT, required = false) String account,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(required = false) Integer limit) {
        return ProductPageResponse.from(products.list(seller(authorization, account, ApiKeyModels.PRODUCTS), cursor, limit));
    }

    @GetMapping("/v1/public/products/{productId}")
    @Operation(summary = "Detalhar um produto")
    public ProductResponse product(@RequestHeader(name = "Authorization", required = false) String authorization,
                                   @RequestHeader(name = ACCOUNT, required = false) String account,
                                   @PathVariable UUID productId) {
        return ProductResponse.from(products.get(seller(authorization, account, ApiKeyModels.PRODUCTS), productId));
    }

    // ---------- Vendas ----------

    @GetMapping("/v1/public/sales")
    @Operation(summary = "Listar vendas com filtros e paginação")
    public SalesPage sales(@RequestHeader(name = "Authorization", required = false) String authorization,
                           @RequestHeader(name = ACCOUNT, required = false) String account,
                           @RequestParam(defaultValue = "all") String tab,
                           @RequestParam(name = "q", required = false) String query,
                           @RequestParam(name = "status", required = false) List<String> statuses,
                           @RequestParam(required = false) String method,
                           @RequestParam(required = false) UUID productId,
                           @RequestParam(required = false) String from,
                           @RequestParam(required = false) String to,
                           @RequestParam(required = false) Integer page,
                           @RequestParam(required = false) Integer size) {
        UUID seller = seller(authorization, account, ApiKeyModels.SALES);
        return sales.list(seller, sales.filter(tab, query, statuses, method, productId, from, to), page, size);
    }

    @GetMapping("/v1/public/sales/{chargeId}")
    @Operation(summary = "Detalhar uma venda")
    public SaleDetail sale(@RequestHeader(name = "Authorization", required = false) String authorization,
                           @RequestHeader(name = ACCOUNT, required = false) String account,
                           @PathVariable UUID chargeId) {
        return sales.detail(seller(authorization, account, ApiKeyModels.SALES), chargeId);
    }

    @PostMapping("/v1/public/sales/{chargeId}/refund")
    @Operation(summary = "Reembolsar uma venda, total (sem amountCents) ou parcial. Exige Idempotency-Key")
    public ResponseEntity<RefundOut> refund(@RequestHeader(name = "Authorization", required = false) String authorization,
                                            @RequestHeader(name = ACCOUNT, required = false) String account,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey,
                                            @PathVariable UUID chargeId,
                                            @RequestBody(required = false) RefundRequest request) {
        UUID seller = seller(authorization, account, ApiKeyModels.SALES_REFUND);
        var body = request == null ? new RefundRequest(null, null) : request;
        var result = refunds.refund(seller, chargeId, new RefundCommand(body.amountCents(), body.reason(), idempotencyKey));
        return ResponseEntity.status(result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new RefundOut(result.refundId(), result.status(), result.sellerCents(), result.affiliateCents(),
                        result.platformCents(), result.providerCents(), result.chargeRefundedCents(), result.chargeStatus()));
    }

    // ---------- Afiliados ----------

    @GetMapping("/v1/public/affiliates")
    @Operation(summary = "Listar os afiliados dos seus produtos")
    public AffiliationPageResponse affiliates(@RequestHeader(name = "Authorization", required = false) String authorization,
                                              @RequestHeader(name = ACCOUNT, required = false) String account,
                                              @RequestParam(required = false) String cursor,
                                              @RequestParam(required = false) Integer limit) {
        return AffiliationPageResponse.from(affiliations.list(seller(authorization, account, ApiKeyModels.AFFILIATES),
                AffiliationRole.SELLER, cursor, limit));
    }

    // ---------- Financeiro ----------

    @GetMapping("/v1/public/balance")
    @Operation(summary = "Saldos, chave Pix e regras de saque")
    public Overview balance(@RequestHeader(name = "Authorization", required = false) String authorization,
                            @RequestHeader(name = ACCOUNT, required = false) String account) {
        return finance.overview(seller(authorization, account, ApiKeyModels.FINANCE));
    }

    @GetMapping("/v1/public/payouts")
    @Operation(summary = "Listar saques")
    public PayoutsPage payouts(@RequestHeader(name = "Authorization", required = false) String authorization,
                               @RequestHeader(name = ACCOUNT, required = false) String account,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        return finance.payoutsPage(seller(authorization, account, ApiKeyModels.FINANCE), page, size);
    }

    // ---------- Relatórios ----------

    @GetMapping("/v1/public/reports/{reportId}")
    @Operation(summary = "Consultar um relatório (produto, afiliado, abandonadas, saldo-receber, recebiveis-cartao, assinaturas-canceladas)")
    public Report report(@RequestHeader(name = "Authorization", required = false) String authorization,
                         @RequestHeader(name = ACCOUNT, required = false) String account,
                         @PathVariable String reportId,
                         @RequestParam(required = false) String from,
                         @RequestParam(required = false) String to,
                         @RequestParam(required = false) UUID productId,
                         @RequestParam(name = "q", required = false) String query,
                         @RequestParam(required = false) String tab,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        return reports.report(seller(authorization, account, ApiKeyModels.REPORTS), reportId,
                reports.filter(from, to, productId, query, tab), page, size);
    }

    // ---------- Webhooks ----------

    @GetMapping("/v1/public/webhooks")
    @Operation(summary = "Listar endpoints de webhook")
    public List<WebhookEndpointService.EndpointView> webhooks(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                              @RequestHeader(name = ACCOUNT, required = false) String account) {
        return webhooks.list(seller(authorization, account, ApiKeyModels.WEBHOOKS));
    }

    @PostMapping("/v1/public/webhooks")
    @Operation(summary = "Criar um endpoint de webhook; o segredo de assinatura vem só nesta resposta")
    public ResponseEntity<WebhookEndpointService.CreatedEndpoint> createWebhook(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @RequestBody WebhookRequest request) {
        UUID seller = seller(authorization, account, ApiKeyModels.WEBHOOKS);
        return ResponseEntity.status(HttpStatus.CREATED).body(webhooks.create(seller, request.url(), request.events(), request.enabled()));
    }

    @PutMapping("/v1/public/webhooks/{endpointId}")
    @Operation(summary = "Editar um endpoint de webhook")
    public WebhookEndpointService.EndpointView updateWebhook(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @PathVariable UUID endpointId, @RequestBody WebhookRequest request) {
        return webhooks.update(seller(authorization, account, ApiKeyModels.WEBHOOKS), endpointId, request.url(), request.events(), request.enabled());
    }

    private UUID seller(String authorization, String account, String scope) {
        return auth.authenticate(authorization, account, scope).accountId();
    }
}
