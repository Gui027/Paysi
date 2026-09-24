package com.paysi.checkout.charge.port;

import com.paysi.payment.provider.ProviderBuyer;

import java.util.Optional;
import java.util.UUID;

public interface CardTokenOrderLookup {
    /** Comprador do pedido, se o pedido existe, está pendente e foi criado para pagar com cartão. */
    Optional<ProviderBuyer> findBuyerOfPendingCardOrder(UUID orderId);
}
