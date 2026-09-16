package com.paysi.checkout.order.port;

import com.paysi.checkout.order.domain.Order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    /**
     * Insere o pedido. O índice único {@code (offer_id, idempotency_key)} é a
     * autoridade: se a chave já foi usada, nada é gravado.
     *
     * @return {@code false} quando a chave já existia — quem chama deve desfazer a
     *         transação e devolver o pedido original.
     */
    boolean insertIfAbsent(Order order);

    /** Pedido já gravado para esta chave, usado para responder a repetição. */
    Optional<Order> findByIdempotencyKey(UUID offerId, String idempotencyKey);
}
