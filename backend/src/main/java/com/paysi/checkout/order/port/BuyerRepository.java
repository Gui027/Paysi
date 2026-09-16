package com.paysi.checkout.order.port;

import com.paysi.checkout.order.domain.Buyer;

import java.time.Instant;
import java.util.Optional;

public interface BuyerRepository {

    /** Chave de reuso é {@code (tax_id, email)} entre os registros não anonimizados. */
    Optional<Buyer> findActive(String taxId, String email);

    /**
     * Insere e devolve o comprador. Duas requisições simultâneas do mesmo comprador
     * não podem criar duas linhas: o índice único parcial decide, e o perdedor relê.
     */
    Buyer insertOrRead(Buyer buyer, Instant createdAt);
}
