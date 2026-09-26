package com.paysi.checkout.order.domain;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.core.error.ValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Intenção de compra já resolvida pelo servidor. Nenhum valor aqui veio do navegador:
 * o bruto é relido da oferta e o desconto sai do cupom (documento 5, passo 5).
 *
 * @param buyerSnapshot retrato imutável do comprador, para prova de contestação
 * @param requestHash impressão do corpo aceito; a mesma chave com corpo diferente é 409
 */
public record Order(
        UUID id,
        UUID offerId,
        UUID buyerId,
        UUID affiliationId,
        String buyerSnapshot,
        long grossCents,
        long discountCents,
        UUID couponId,
        long paidCents,
        OfferPaymentMethod method,
        int installments,
        OrderStatus status,
        String idempotencyKey,
        String requestHash,
        Instant createdAt,
        String externalRef,
        String buyerPhone,
        String buyerIp
) {
    /** Pedido sem celular nem IP; mantém a assinatura anterior. */
    public Order(UUID id, UUID offerId, UUID buyerId, UUID affiliationId, String buyerSnapshot, long grossCents,
                 long discountCents, UUID couponId, long paidCents, OfferPaymentMethod method, int installments,
                 OrderStatus status, String idempotencyKey, String requestHash, Instant createdAt,
                 String externalRef) {
        this(id, offerId, buyerId, affiliationId, buyerSnapshot, grossCents, discountCents, couponId, paidCents,
                method, installments, status, idempotencyKey, requestHash, createdAt, externalRef, null, null);
    }

    /** Pedido sem referência externa; mantém a assinatura anterior. */
    public Order(UUID id, UUID offerId, UUID buyerId, UUID affiliationId, String buyerSnapshot, long grossCents,
                 long discountCents, UUID couponId, long paidCents, OfferPaymentMethod method, int installments,
                 OrderStatus status, String idempotencyKey, String requestHash, Instant createdAt) {
        this(id, offerId, buyerId, affiliationId, buyerSnapshot, grossCents, discountCents, couponId, paidCents,
                method, installments, status, idempotencyKey, requestHash, createdAt, null, null, null);
    }

    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(offerId, "offerId");
        Objects.requireNonNull(buyerId, "buyerId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (buyerSnapshot == null || buyerSnapshot.isBlank()) {
            throw invalid("O retrato do comprador é obrigatório", "buyer");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key é obrigatório", "Idempotency-Key");
        }
        if (requestHash == null || requestHash.isBlank()) {
            throw invalid("A impressão da requisição é obrigatória", null);
        }
        if (discountCents < 0) throw invalid("O desconto não pode ser negativo", "coupon");
        if (paidCents != Math.subtractExact(grossCents, discountCents)) {
            throw invalid("O valor pago deve ser o bruto menos o desconto", null);
        }
        if (installments < 1) throw invalid("O parcelamento deve ser de ao menos uma vez", "installments");
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("ORDER_INVALID", message, field);
    }
}
