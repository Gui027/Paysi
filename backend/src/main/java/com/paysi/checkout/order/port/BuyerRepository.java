package com.paysi.checkout.order.port;

import com.paysi.checkout.order.domain.Buyer;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface BuyerRepository {

    /** Chave de reuso é {@code (tax_id, email)} entre os registros não anonimizados. */
    Optional<Buyer> findActive(String taxId, String email);

    /**
     * Insere e devolve o comprador. Duas requisições simultâneas do mesmo comprador
     * não podem criar duas linhas: o índice único parcial decide, e o perdedor relê.
     */
    Buyer insertOrRead(Buyer buyer, Instant createdAt);

    /** Existe algum registro (anonimizado ou não) com esse id? Usado para validar pedido LGPD. */
    boolean exists(UUID id);

    /**
     * BE-14.4: apaga PII do registro vivo do comprador, preservando o id (que
     * pedidos e o razão continuam referenciando) e marca {@code anonymized_at}.
     * Idempotente — anonimizar de novo é um no-op silencioso. Nunca toca
     * {@code orders.buyer_snapshot} nem qualquer lançamento do razão: o retrato
     * congelado da venda é prova legal de contestação e o razão é imutável
     * (documento 2, §3.4 e §3.12; V011 impede update/delete em ledger_entries).
     */
    boolean anonymize(UUID id, Instant anonymizedAt);
}
