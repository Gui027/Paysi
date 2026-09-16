package com.paysi.subscription.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.provider.*;
import com.paysi.payment.split.PaymentMethod;
import com.paysi.payment.split.Plan;
import com.paysi.payment.split.Split;
import com.paysi.payment.split.SplitEngine;
import com.paysi.subscription.domain.Subscription;
import com.paysi.subscription.domain.SubscriptionCharge;
import com.paysi.subscription.domain.SubscriptionStatus;
import com.paysi.subscription.port.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BE-10.1: cria a assinatura e processa a cobrança do primeiro ciclo (ou abre teste, quando aplicável).
 * A submissão de checkout genérica (comprador/pedido/cupom para ofertas avulsas) é escopo de BE-05.2;
 * este serviço cobre apenas o caminho de ofertas de assinatura, reaproveitando as tabelas orders/charges.
 */
@Service
public class SubscriptionService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SubscriptionRepository subscriptions;
    private final OfferRepository offers;
    private final PlatformPlanReader plans;
    private final PaymentProvider provider;
    private final ObjectMapper json;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public SubscriptionService(SubscriptionRepository subscriptions, OfferRepository offers,
                                PlatformPlanReader plans, PaymentProvider provider, ObjectMapper json) {
        this(subscriptions, offers, plans, provider, json, Clock.systemUTC());
    }

    SubscriptionService(SubscriptionRepository subscriptions, OfferRepository offers, PlatformPlanReader plans,
                         PaymentProvider provider, ObjectMapper json, Clock clock) {
        this.subscriptions = subscriptions;
        this.offers = offers;
        this.plans = plans;
        this.provider = provider;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public SubscriptionCreationResult create(CreateSubscriptionCommand command) {
        Offer offer = offers.findPublishedBySlug(command.offerSlug()).orElseThrow(SubscriptionService::offerNotFound);
        if (offer.chargeType() != ChargeType.SUBSCRIPTION) {
            throw new ValidationException("OFFER_NOT_SUBSCRIPTION", "Esta oferta não é uma assinatura", "offerSlug");
        }
        requireBuyerFields(command);
        boolean trial = offer.trialDays() > 0;
        if ((!trial || offer.trialRequiresCard()) && blank(command.cardToken())) {
            throw new ValidationException("CARD_TOKEN_REQUIRED", "Token do cartão é obrigatório", "cardToken");
        }

        String requestHash = hash(command);
        var replay = subscriptions.findOrderByIdempotency(offer.id(), command.idempotencyKey());
        if (replay.isPresent()) {
            if (!replay.get().requestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT",
                        "Já existe uma solicitação diferente com esta chave", "idempotencyKey");
            }
            return new SubscriptionCreationResult(replay.get().subscriptionId(), true);
        }

        UUID sellerId = subscriptions.sellerIdForOffer(offer.id()).orElseThrow(SubscriptionService::offerNotFound);
        UUID buyerId = resolveBuyer(command);
        Instant now = clock.instant();

        UUID orderId = UUID.randomUUID();
        String snapshot = writeJson(new BuyerSnapshot(command.buyerName(), command.buyerEmail(),
                command.personType(), command.taxId(), command.legalName(), command.municipalReg()));
        boolean inserted = subscriptions.insertOrder(orderId, offer.id(), buyerId, snapshot, offer.priceCents(),
                command.idempotencyKey(), requestHash, now);
        if (!inserted) {
            // corrida perdida: outra requisição com a mesma chave venceu; devolve o resultado dela.
            var raced = subscriptions.findOrderByIdempotency(offer.id(), command.idempotencyKey())
                    .orElseThrow(() -> new IllegalStateException("Pedido sumiu após corrida de idempotência"));
            return new SubscriptionCreationResult(raced.subscriptionId(), true);
        }

        UUID subscriptionId = UUID.randomUUID();
        if (trial) {
            Instant trialEndsAt = now.plus(java.time.Duration.ofDays(offer.trialDays()));
            subscriptions.insertSubscription(new Subscription(subscriptionId, orderId, offer.id(),
                    SubscriptionStatus.TRIAL, 0, trialEndsAt, trialEndsAt, null, command.cardToken(), now));
            subscriptions.markOrderStatus(orderId, "PAID", now);
            return new SubscriptionCreationResult(subscriptionId, false);
        }

        subscriptions.insertSubscription(new Subscription(subscriptionId, orderId, offer.id(),
                SubscriptionStatus.ACTIVE, 1, null, null, null, command.cardToken(), now));
        chargeCycle(sellerId, offer, orderId, subscriptionId, 1, command.cardToken(),
                new ProviderBuyer(command.buyerName(), command.buyerEmail(), command.personType(), command.taxId()));
        return new SubscriptionCreationResult(subscriptionId, false);
    }

    private void chargeCycle(UUID sellerId, Offer offer, UUID orderId, UUID subscriptionId, int cycleNumber,
                              String cardToken, ProviderBuyer buyer) {
        Instant nextChargeAt = nextCharge(clock.instant(), offer.cycle());
        Plan plan = Plan.valueOf(plans.currentPlan(sellerId));
        Split split = SplitEngine.split(offer.priceCents(), PaymentMethod.CARD_1, plan, 0);
        UUID chargeId = UUID.randomUUID();
        Instant now = clock.instant();
        subscriptions.insertCharge(chargeId, orderId, subscriptionId, cycleNumber, offer.priceCents(),
                plan.name(), PaymentMethod.CARD_1.feeBps(plan), 200, split.sellerFeeCents(),
                split.affiliateCents(), split.sellerCents(), "PENDING", now);

        var result = provider.charge(new ProviderPaymentRequest(orderId, offer.priceCents(),
                ProviderPaymentMethod.CARD, 1, cardToken, buyer,
                new ProviderSplit(split.sellerCents(), split.affiliateCents(), split.sellerFeeCents())));

        boolean approved = result.status() == ProviderChargeStatus.APPROVED;
        subscriptions.saveChargeResult(chargeId, approved ? "PAID" : "FAILED", result.providerChargeId(),
                result.providerFeeCents(), approved ? now : null, approved ? now : null);
        subscriptions.markOrderStatus(orderId, approved ? "PAID" : "FAILED", approved ? now : null);
        subscriptions.updateSubscriptionCycle(subscriptionId,
                (approved ? SubscriptionStatus.ACTIVE : SubscriptionStatus.PAST_DUE).name(),
                approved ? nextChargeAt : null);
    }

    private static Instant nextCharge(Instant now, BillingCycle cycle) {
        int months = switch (cycle) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case SEMIANNUAL -> 6;
            case ANNUAL -> 12;
        };
        return ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(months).toInstant();
    }

    @Transactional(readOnly = true)
    public SubscriptionPage list(UUID sellerId, String cursor, Integer limit) {
        int size = normalizeLimit(limit);
        var decoded = SubscriptionCursorCodec.decode(cursor);
        var items = subscriptions.listForSeller(sellerId, decoded == null ? null : decoded.createdAt(),
                decoded == null ? null : decoded.id(), size + 1);
        boolean hasMore = items.size() > size;
        var page = hasMore ? items.subList(0, size) : items;
        String next = hasMore
                ? SubscriptionCursorCodec.encode(new SubscriptionCursor(
                        page.get(page.size() - 1).createdAt(), page.get(page.size() - 1).id()))
                : null;
        return new SubscriptionPage(page, next);
    }

    @Transactional(readOnly = true)
    public SubscriptionDetail detail(UUID sellerId, UUID subscriptionId) {
        Subscription subscription = subscriptions.findOwned(sellerId, subscriptionId)
                .orElseThrow(SubscriptionService::subscriptionNotFound);
        List<SubscriptionCharge> charges = subscriptions.listCharges(subscriptionId);
        return new SubscriptionDetail(subscription, charges);
    }

    @Transactional
    public void cancelAtPeriodEnd(UUID sellerId, UUID subscriptionId) {
        boolean changed = subscriptions.requestCancelAtPeriodEnd(sellerId, subscriptionId, clock.instant());
        if (!changed) {
            subscriptions.findOwned(sellerId, subscriptionId).orElseThrow(SubscriptionService::subscriptionNotFound);
            // já existe pedido de cancelamento ou a assinatura já foi encerrada: operação é idempotente.
        }
    }

    private UUID resolveBuyer(CreateSubscriptionCommand command) {
        return subscriptions.findBuyer(command.taxId(), command.buyerEmail())
                .orElseGet(() -> subscriptions.insertBuyer(UUID.randomUUID(), command.buyerName(),
                        command.buyerEmail(), command.personType(), command.taxId(), command.legalName(),
                        command.municipalReg(), command.addressJson() == null ? "{}" : command.addressJson(),
                        clock.instant()));
    }

    private static void requireBuyerFields(CreateSubscriptionCommand command) {
        if (blank(command.buyerName()) || blank(command.buyerEmail()) || blank(command.taxId())
                || blank(command.idempotencyKey())) {
            throw new ValidationException("BUYER_FIELDS_REQUIRED", "Dados do comprador são obrigatórios", null);
        }
        if (!Set.of("PF", "PJ").contains(command.personType())) {
            throw new ValidationException("PERSON_TYPE_INVALID", "Tipo de pessoa inválido", "personType");
        }
        if ("PJ".equals(command.personType()) && blank(command.legalName())) {
            throw new ValidationException("LEGAL_NAME_REQUIRED", "Razão social é obrigatória para PJ", "legalName");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException("Falha ao serializar dados do comprador", error);
        }
    }

    static String hash(CreateSubscriptionCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = String.join("|", command.offerSlug(), command.buyerName(), command.buyerEmail(),
                    command.personType(), command.taxId(), String.valueOf(command.legalName()),
                    String.valueOf(command.municipalReg()), String.valueOf(command.cardToken()));
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static NotFoundException offerNotFound() {
        return new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada");
    }

    private static NotFoundException subscriptionNotFound() {
        return new NotFoundException("SUBSCRIPTION_NOT_FOUND", "Assinatura não encontrada");
    }

    private record BuyerSnapshot(String name, String email, String personType, String taxId,
                                  String legalName, String municipalReg) {
    }

    public record SubscriptionCreationResult(UUID subscriptionId, boolean idempotentReplay) {
    }

    public record SubscriptionDetail(Subscription subscription, List<SubscriptionCharge> charges) {
    }
}
