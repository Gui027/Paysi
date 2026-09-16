package com.paysi.checkout.pricing.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Dono do produto ao qual a oferta pertence. O preço é do catálogo, mas a faixa de
 * taxa é do plano comercial do vendedor, e a oferta não carrega essa identidade.
 */
public interface OfferSellerLookup {
    Optional<UUID> findSellerId(UUID productId);
}
