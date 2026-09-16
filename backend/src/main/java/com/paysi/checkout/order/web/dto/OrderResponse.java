package com.paysi.checkout.order.web.dto;

import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.checkout.order.domain.Order;
import com.paysi.checkout.order.domain.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido criado. A divisão, o custo do provedor e a data de liberação entram quando a
 * cobrança passar a existir (BE-07.1); até lá o checkout já pode confirmar ao
 * comprador o que foi registrado e por quanto.
 */
public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        long grossCents,
        long discountCents,
        long paidCents,
        OfferPaymentMethod method,
        int installments,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.id(), order.status(), order.grossCents(),
                order.discountCents(), order.paidCents(), order.method(), order.installments(),
                order.createdAt());
    }
}
