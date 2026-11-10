package com.paysi.apikey.web;

import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.checkout.charge.app.CardTokenService;
import com.paysi.checkout.charge.app.ChargeCreationService;
import com.paysi.checkout.charge.app.StartChargeCommand;
import com.paysi.checkout.charge.web.dto.ChargeStartResponse;
import com.paysi.checkout.charge.web.dto.ChargeStatusResponse;
import com.paysi.checkout.order.app.CreateOrderCommand;
import com.paysi.checkout.order.app.CreateOrderService;
import com.paysi.checkout.order.domain.BuyerAddress;
import com.paysi.checkout.order.web.dto.OrderResponse;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.PersonType;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** API de pagamentos server-to-server. Preço, taxas e vendedor são sempre relidos do banco. */
@RestController
@Tag(name = "Pagamentos da API pública")
public class PublicPaymentController {
    private static final String ACCOUNT = "X-Paysi-Account-Id";

    public record BuyerRequest(@NotBlank @Size(max = 200) String name,
                               @NotBlank @Size(max = 320) String email,
                               @NotNull PersonType personType,
                               @NotBlank @Size(max = 32) String taxId,
                               @Size(max = 200) String legalName,
                               @Size(max = 32) String municipalReg,
                               @Valid BuyerAddress address,
                               @Size(max = 32) String phone) { }

    /** Dados são usados somente para tokenização e nunca são persistidos ou incluídos em logs. */
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

    public record CreatePaymentRequest(@NotBlank @Size(max = 200) String offerSlug,
                                       @NotNull @Valid BuyerRequest buyer,
                                       @NotNull OfferPaymentMethod method,
                                       Integer installments,
                                       @Size(max = 32) String coupon,
                                       @Size(max = 128) String reference,
                                       Long amountCents,
                                       @Size(max = 128) String visitorKey,
                                       @Size(max = 64) String customerIp,
                                       @NotBlank @Size(max = 128) String termsHash,
                                       @NotNull Instant termsAcceptedAt,
                                       @Valid CardRequest card) {
        @Override public String toString() { return "CreatePaymentRequest[offerSlug=" + offerSlug
                + ", method=" + method + ", buyer=[REDACTED], card=[REDACTED]]"; }
    }

    public record CreatePaymentResponse(OrderResponse order, ChargeStartResponse payment,
                                        String nextAction) { }

    private final ApiAuthService auth;
    private final OfferRepository offers;
    private final ProductService products;
    private final CreateOrderService orders;
    private final ChargeCreationService charges;
    private final CardTokenService cardTokens;
    private final CheckoutRateLimitGuard rateLimit;

    public PublicPaymentController(ApiAuthService auth, OfferRepository offers, ProductService products,
                                   CreateOrderService orders, ChargeCreationService charges,
                                   CardTokenService cardTokens, CheckoutRateLimitGuard rateLimit) {
        this.auth = auth;
        this.offers = offers;
        this.products = products;
        this.orders = orders;
        this.charges = charges;
        this.cardTokens = cardTokens;
        this.rateLimit = rateLimit;
    }

    @PostMapping("/v1/public/payments")
    @Operation(summary = "Criar e iniciar um pagamento Pix, boleto ou cartão",
            description = "Exige Idempotency-Key. A oferta define o preço e precisa pertencer à conta da API Key.")
    public ResponseEntity<CreatePaymentResponse> create(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = ACCOUNT, required = false) String account,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request, HttpServletRequest httpRequest) {
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.PAYMENTS_WRITE).accountId();
        var offer = offers.findPublishedBySlug(request.offerSlug())
                .orElseThrow(() -> new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));
        products.get(sellerId, offer.productId()); // impede uma chave de cobrar por oferta de outra conta
        if (request.method() == OfferPaymentMethod.CARD && request.card() == null) {
            throw new ValidationException("CARD_REQUIRED", "Informe os dados do cartão para pagamento com cartão", "card");
        }

        // O IP da conexão é o backend do lojista e não deve limitar todas as vendas dele como se fosse um comprador.
        // customerIp é opcional e serve somente como evidência/limite antifraude do consumidor final.
        String ip = normalizedIp(request.customerIp());
        rateLimit.checkOrderAttempt(ip, request.buyer().taxId(), request.visitorKey());
        var buyer = request.buyer();
        var command = new CreateOrderCommand(buyer.name(), buyer.email(), buyer.personType(), buyer.taxId(),
                buyer.legalName(), buyer.municipalReg(), buyer.address(), request.method(),
                request.installments() == null ? 1 : request.installments(), null, request.coupon(),
                request.visitorKey(), request.termsHash(), request.reference(), buyer.phone(), ip,
                request.amountCents());
        var result = orders.create(request.offerSlug(), idempotencyKey, command);
        var order = result.order();

        String cardToken = null;
        if (request.method() == OfferPaymentMethod.CARD) {
            var card = request.card();
            cardToken = cardTokens.tokenize(order.id(), new CardTokenService.CardData(card.holderName(), card.number(),
                    card.expiryMonth(), card.expiryYear(), card.ccv(), card.postalCode(), card.addressNumber(),
                    card.phone()), ip).token();
        }

        var payment = ChargeStartResponse.from(charges.start(order.id(), new StartChargeCommand(cardToken, ip,
                httpRequest.getHeader("User-Agent"), request.visitorKey(), request.termsHash(),
                request.termsAcceptedAt())));
        return ResponseEntity.status(result.replay() || payment.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new CreatePaymentResponse(OrderResponse.from(order), payment, nextAction(payment)));
    }

    @GetMapping("/v1/public/payments/{paymentId}")
    @Operation(summary = "Consultar um pagamento da própria conta")
    public ChargeStatusResponse get(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestHeader(name = ACCOUNT, required = false) String account,
                                    @PathVariable UUID paymentId) {
        UUID sellerId = auth.authenticate(authorization, account, ApiKeyModels.PAYMENTS_READ).accountId();
        return ChargeStatusResponse.from(charges.viewOwned(sellerId, paymentId));
    }

    private static String nextAction(ChargeStartResponse payment) {
        if (payment.threeDs() != null && payment.threeDs().required()) return "complete_3ds";
        if ("PIX".equals(payment.method())) return "display_pix_qr_code";
        if ("BOLETO".equals(payment.method())) return "display_boleto";
        return "none";
    }

    private static String normalizedIp(String value) {
        if (value == null || value.isBlank()) return null;
        return value.strip();
    }
}
