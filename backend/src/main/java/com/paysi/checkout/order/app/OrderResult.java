package com.paysi.checkout.order.app;

import com.paysi.checkout.order.domain.Order;

/**
 * Pedido criado, ou o mesmo pedido devolvido de novo para a requisição repetida.
 *
 * @param replay verdadeiro quando a chave de idempotência já havia sido usada com o
 *               mesmo corpo: nada foi criado desta vez
 */
public record OrderResult(Order order, boolean replay) {

    static OrderResult created(Order order) {
        return new OrderResult(order, false);
    }

    static OrderResult replayed(Order order) {
        return new OrderResult(order, true);
    }
}
