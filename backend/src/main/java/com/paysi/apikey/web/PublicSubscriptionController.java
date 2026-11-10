package com.paysi.apikey.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.provider.CardTokenRequest;
import com.paysi.payment.provider.CardTokenizationException;
import com.paysi.payment.provider.CardTokenizer;
import com.paysi.payment.provider.ProviderBuyer;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import com.paysi.subscription.app.CreateSubscriptionCommand;
import com.paysi.subscription.app.SubscriptionService;
import com.paysi.subscription.web.dto.SubscriptionDetailResponse;
import com.paysi.subscription.web.dto.SubscriptionPageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** API server-to-server de assinaturas recorrentes, isolada pela conta da API Key. */
@RestController
@Tag(name = "Assinaturas da API pública")
public class PublicSubscriptionController {
    private static final String ACCOUNT = "X-Paysi-Account-Id";

    public record BuyerRequest(@NotBlank @Size(max = 200) String name,
                               @NotBlank @Email @Size(max = 320) String email,
                               @NotBlank @Pattern(regexp = "PF|PJ") String personType,
                               @NotBlank @Size(max = 32) String taxId,
                               @Size(max = 200) String legalName,
                               @Size(max = 32) String municipalReg,
                               JsonNode address) { }

    /** O PAN e o CVV vivem somente durante a tokenização e nunca entram em persistência ou logs. */
    public record CardRequest(@NotBlank @Size(max = 80) String holderName,
                              @NotBlank @Pattern(regexp = "\\d{13,19}") String number,
                              @NotBlank @Pattern(regexp = "\\d{1,2}") String expiryMonth,
                              @NotBlank @Pattern(regexp = "\\d{4}") String expiryYear,
                              @NotBlank @Pattern(regexp = "\\d{3,4}") String ccv,
                              @NotBlank @Pattern(regexp = "\\d{8}") String postalCode,
                              @NotBlank @Size(max = 10) String addressNumber,
                              @NotBlank @Pattern(regexp = "\\d{10,13}") String phone) {
        @Override public String toString() { return "CardRequest[REDACTED]"; }
    }

    public record CreateSubscriptionRequest(@NotBlank @Size(max = 200) String offerSlug,
                                            @NotNull @Valid BuyerRequest buyer,
                                            @Pattern(regexp = "(?i)CARD", message = "A API pública aceita CARD para recorrência") String method,
                                            @Size(max = 128) String visitorKey,
                                            @Size(max = 64) String customerIp,
                                            @Valid CardRequest card) {
        @Override public String toString() {
            return "CreateSubscriptionRequest[offerSlug=" + offerSlug + ", buyer=[REDACTED], card=[REDACTED]]";
        }
    }

    private final ApiAuthService auth;
    private final OfferRepository offers;
    private final ProductService products;
    private final SubscriptionService subscriptions;
    private final CardTokenizer tokenizer;
    private final CheckoutRateLimitGuard rateLimit;

    public PublicSubscriptionController(ApiAuthService auth, OfferRepository offers, ProductService products,
                                        SubscriptionService subscriptions, CardTokenizer tokenizer,
                                        CheckoutRateLimitGuard rateLimit) {
        this.auth = auth;
        this.offers = offers;
        this.products = products;
        this.subscriptions = subscriptions;
        this.tokenizer = tokenizer;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/v1/public/subscriptions")
    @Operation(summary = "Criar assinatura recorrente",
            description = "Exige Idempotency-Key e uma oferta de assinatura publicada pertencente à conta da chave.")
    public ResponseEntity<SubscriptionDetailResponse> create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateSubscriptionRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    "Header Idempotency-Key é obrigatório e deve ter até 128 caracteres", "Idempotency-Key");
        }
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.SUBSCRIPTIONS_WRITE).accountId();
        var offer = offers.findPublishedBySlug(request.offerSlug())
                .orElseThrow(() -> new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));
        products.get(sellerId, offer.productId());

        String ip = normalized(request.customerIp());
        var buyer = request.buyer();
        String addressJson = buyer.address() == null ? null : buyer.address().toString();
        var replayCommand = new CreateSubscriptionCommand(request.offerSlug(), buyer.name(), buyer.email(),
                buyer.personType(), buyer.taxId(), buyer.legalName(), buyer.municipalReg(), addressJson,
                null, "CARD", request.visitorKey(), idempotencyKey);
        var replay = subscriptions.findReplay(replayCommand);
        if (replay.isPresent()) return response(sellerId, replay.get());

        rateLimit.checkOrderAttempt(ip, buyer.taxId(), request.visitorKey());
        String cardToken = request.card() == null ? null : tokenize(sellerId, request, idempotencyKey, ip);
        var command = new CreateSubscriptionCommand(request.offerSlug(), buyer.name(), buyer.email(),
                buyer.personType(), buyer.taxId(), buyer.legalName(), buyer.municipalReg(), addressJson,
                cardToken, "CARD", request.visitorKey(), idempotencyKey);
        var result = subscriptions.create(command);
        return response(sellerId, result);
    }

    private ResponseEntity<SubscriptionDetailResponse> response(
            UUID sellerId, SubscriptionService.SubscriptionCreationResult result) {
        var body = SubscriptionDetailResponse.from(subscriptions.detail(sellerId, result.subscriptionId()));
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/v1/public/subscriptions/" + result.subscriptionId()))
                .body(body);
    }

    @GetMapping("/v1/public/subscriptions")
    @Operation(summary = "Listar assinaturas da própria conta")
    public SubscriptionPageResponse list(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.SUBSCRIPTIONS_READ).accountId();
        return SubscriptionPageResponse.from(subscriptions.list(sellerId, cursor, limit));
    }

    @GetMapping("/v1/public/subscriptions/{subscriptionId}")
    @Operation(summary = "Consultar assinatura da própria conta")
    public SubscriptionDetailResponse detail(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @PathVariable UUID subscriptionId) {
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.SUBSCRIPTIONS_READ).accountId();
        return SubscriptionDetailResponse.from(subscriptions.detail(sellerId, subscriptionId));
    }

    @PostMapping("/v1/public/subscriptions/{subscriptionId}/cancel")
    @Operation(summary = "Cancelar assinatura ao fim do período vigente")
    public ResponseEntity<Void> cancel(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @PathVariable UUID subscriptionId) {
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.SUBSCRIPTIONS_WRITE).accountId();
        subscriptions.cancelAtPeriodEnd(sellerId, subscriptionId);
        return ResponseEntity.noContent().build();
    }

    private String tokenize(UUID sellerId, CreateSubscriptionRequest request, String idempotencyKey, String ip) {
        UUID attemptId = UUID.nameUUIDFromBytes((sellerId + ":" + request.offerSlug() + ":" + idempotencyKey)
                .getBytes(StandardCharsets.UTF_8));
        rateLimit.checkCardTokenAttempt(ip, attemptId);
        var buyer = request.buyer();
        var card = request.card();
        try {
            return tokenizer.tokenize(new CardTokenRequest(
                    new ProviderBuyer(buyer.name(), buyer.email(), buyer.personType(), buyer.taxId()),
                    card.holderName(), card.number(), card.expiryMonth(), card.expiryYear(), card.ccv(),
                    card.postalCode(), card.addressNumber(), card.phone(), ip)).token();
        } catch (CardTokenizationException error) {
            throw new ValidationException("CARD_TOKENIZATION_FAILED", error.getMessage(), "card");
        }
    }

    private static String normalized(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
