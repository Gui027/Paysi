package com.paysi.checkout.order.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Guarda rápida de idempotência (ADR-08). A gravação é {@code SET NX}, não
 * {@code GET} seguido de {@code SET}: sem isso, duas requisições idênticas
 * simultâneas passariam as duas pela verificação antes de qualquer uma gravar.
 *
 * <p>É uma guarda, não a autoridade. Quem decide em definitivo é o índice único
 * {@code (offer_id, idempotency_key)} — se o Redis cair, a corretude não depende dele.
 */
public interface IdempotencyLock {

    /** @return {@code true} quando esta requisição foi a primeira a marcar a chave. */
    boolean acquire(String scope, String key, String requestHash, Duration ttl);

    /** Impressão registrada por quem chegou antes, quando houver. */
    Optional<String> requestHashOf(String scope, String key);
}
