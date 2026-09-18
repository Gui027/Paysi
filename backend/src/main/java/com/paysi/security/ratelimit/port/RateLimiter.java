package com.paysi.security.ratelimit.port;

import java.time.Duration;

/**
 * Limite de tentativas por chave dentro de uma janela deslizante fixa (AM-05, AM-22).
 *
 * <p>É deliberadamente tolerante a falha do backend de contagem: uma indisponibilidade
 * do Redis não pode derrubar o checkout — o mesmo princípio de {@code IdempotencyLock}.
 * Sem o contador, a requisição segue liberada e a decisão de bloqueio some junto com
 * a proteção, nunca ao contrário.
 */
public interface RateLimiter {

    /**
     * @param key        identificador já namespaced (ex.: {@code "checkout:order:ip:1.2.3.4"})
     * @param maxAttempts tentativas permitidas dentro da janela, inclusive esta
     * @param window     duração da janela
     * @return {@code true} quando a tentativa é permitida
     */
    boolean allow(String key, int maxAttempts, Duration window);
}
