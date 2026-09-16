package com.paysi.checkout.order.domain;

/**
 * Ciclo de vida do pedido. Reembolso e contestação vivem na cobrança, não aqui
 * (FIX-D04); o estado consolidado sai da visão {@code v_order_status}.
 */
public enum OrderStatus {
    PENDING,
    PAID,
    FAILED,
    EXPIRED
}
