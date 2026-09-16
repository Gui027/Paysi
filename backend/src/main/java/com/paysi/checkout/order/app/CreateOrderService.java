package com.paysi.checkout.order.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.catalog.coupon.app.CouponDiscount;
import com.paysi.catalog.coupon.app.CouponRedemptionService;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.checkout.order.domain.Buyer;
import com.paysi.checkout.order.domain.BuyerSnapshot;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;
import com.paysi.checkout.order.port.AffiliationClickLookup;
import com.paysi.checkout.order.port.BuyerRepository;
import com.paysi.checkout.order.port.IdempotencyLock;
import com.paysi.checkout.order.port.OrderRepository;
import com.paysi.checkout.pricing.app.PriceQuote;
import com.paysi.checkout.pricing.app.PriceSimulationService;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Criação do pedido do checkout público.
 *
 * <p>A ordem dos passos é a corretude, não uma preferência de estilo:
 * <ol>
 *   <li>a oferta é relida do banco e o valor recalculado — nada de preço vem do
 *       navegador (documento 5, passo 5, controle contra AM-15);</li>
 *   <li>o pedido é inserido com {@code on conflict do nothing} sobre
 *       {@code (offer_id, idempotency_key)}, que é a autoridade da idempotência;</li>
 *   <li>só <em>depois</em> que o pedido foi realmente gravado é que a unidade do cupom
 *       é consumida — do contrário, uma requisição repetida gastaria estoque;</li>
 *   <li>nenhum lançamento no razão: o razão só é tocado na confirmação do pagamento
 *       (documento 5, passo 8 contra 8c).</li>
 * </ol>
 *
 * <p>A cobrança, a memória de divisão persistida e a chamada ao provedor pertencem ao
 * BE-07.1. Por isso o {@code cardToken} é validado e descartado aqui: ele não é
 * gravado nem registrado em log.
 */
@Service
public class CreateOrderService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final OfferRepository offers;
    private final OrderRepository orders;
    private final BuyerRepository buyers;
    private final PriceSimulationService prices;
    private final CouponRedemptionService coupons;
    private final AffiliationClickLookup clicks;
    private final IdempotencyLock locks;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public CreateOrderService(OfferRepository offers, OrderRepository orders, BuyerRepository buyers,
            PriceSimulationService prices, CouponRedemptionService coupons,
            AffiliationClickLookup clicks, IdempotencyLock locks, ObjectMapper json) {
        this(offers, orders, buyers, prices, coupons, clicks, locks, json, Clock.systemUTC());
    }

    CreateOrderService(OfferRepository offers, OrderRepository orders, BuyerRepository buyers,
            PriceSimulationService prices, CouponRedemptionService coupons,
            AffiliationClickLookup clicks, IdempotencyLock locks, ObjectMapper json, Clock clock) {
        this.offers = offers;
        this.orders = orders;
        this.buyers = buyers;
        this.prices = prices;
        this.coupons = coupons;
        this.clicks = clicks;
        this.locks = locks;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public OrderResult create(String slug, String idempotencyKey, CreateOrderCommand command) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key é obrigatório", "Idempotency-Key");
        }
        Offer offer = offers.findPublishedBySlug(slug)
                .orElseThrow(() -> new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));

        String scope = "order:" + offer.id();
        String requestHash = RequestHash.of(command);
        guard(scope, idempotencyKey, requestHash);

        if (command.termsHash() == null || command.termsHash().isBlank()) {
            throw new ValidationException("TERMS_NOT_ACCEPTED",
                    "É necessário aceitar os termos e a política de reembolso", "termsHash");
        }
        if (command.method() != null && requiresCardToken(command)) {
            throw new ValidationException("CARD_TOKEN_REQUIRED",
                    "Informe o token do cartão gerado pelo provedor", "cardToken");
        }

        Buyer buyer = buyers.insertOrRead(newBuyer(offer, command), clock.instant());
        AffiliationClickLookup.Attribution attribution = attribution(offer, command);
        PriceQuote quote = prices.priceFor(offer, command.method(), command.installments(),
                command.coupon(), attribution == null ? 0 : attribution.commissionBps());

        Order order = new Order(UUID.randomUUID(), offer.id(), buyer.id(),
                attribution == null ? null : attribution.affiliationId(),
                snapshot(buyer, command.termsHash()), quote.grossCents(), quote.discountCents(),
                quote.discount().couponId(), quote.paidCents(), quote.method(), quote.installments(),
                OrderStatus.PENDING, idempotencyKey, requestHash, clock.instant());

        if (!orders.insertIfAbsent(order)) {
            return OrderResult.replayed(previous(offer.id(), idempotencyKey, requestHash));
        }

        // Só agora o estoque do cupom é tocado: a repetição acima não chega aqui.
        CouponDiscount discount = quote.discount();
        coupons.reserveUnit(discount);
        coupons.confirm(discount, order.id(), buyer.id());

        return OrderResult.created(order);
    }

    /**
     * Guarda rápida do ADR-08. Recusa a chave reutilizada com outro corpo antes de
     * qualquer escrita; a decisão definitiva continua sendo do índice único.
     */
    private void guard(String scope, String idempotencyKey, String requestHash) {
        if (locks.acquire(scope, idempotencyKey, requestHash, IDEMPOTENCY_TTL)) return;
        locks.requestHashOf(scope, idempotencyKey)
                .filter(seen -> !seen.equals(requestHash))
                .ifPresent(seen -> {
                    throw reused();
                });
    }

    /** Pedido já gravado para esta chave. Corpo diferente aqui também é 409. */
    private Order previous(UUID offerId, String idempotencyKey, String requestHash) {
        Order stored = orders.findByIdempotencyKey(offerId, idempotencyKey)
                .orElseThrow(() -> new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "A chave já foi usada e o pedido original não pôde ser lido",
                        "Idempotency-Key"));
        if (!stored.requestHash().equals(requestHash)) throw reused();
        return stored;
    }

    private Buyer newBuyer(Offer offer, CreateOrderCommand command) {
        return Buyer.create(UUID.randomUUID(), command.name(), command.email(),
                command.personType(), command.taxId(), command.legalName(),
                command.municipalReg(), command.address(), offer.segment());
    }

    private AffiliationClickLookup.Attribution attribution(Offer offer, CreateOrderCommand command) {
        if (command.visitorKey() == null || command.visitorKey().isBlank()) return null;
        return clicks.findLastClick(command.visitorKey(), offer.productId()).orElse(null);
    }

    private String snapshot(Buyer buyer, String termsHash) {
        try {
            return json.writeValueAsString(BuyerSnapshot.of(buyer, termsHash, clock.instant()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Não foi possível gravar o retrato do comprador", error);
        }
    }

    private static boolean requiresCardToken(CreateOrderCommand command) {
        return command.method() == com.paysi.catalog.offer.domain.OfferPaymentMethod.CARD
                && (command.cardToken() == null || command.cardToken().isBlank());
    }

    private static ConflictException reused() {
        return new ConflictException("IDEMPOTENCY_KEY_REUSED",
                "A chave já foi usada com outro conteúdo", "Idempotency-Key");
    }
}
