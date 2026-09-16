package com.paysi.checkout.order.adapter;

import com.paysi.checkout.order.port.IdempotencyLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Guarda de idempotência em Redis (ADR-08), gravada com {@code SET NX}.
 *
 * <p>É deliberadamente tolerante a falha: o Redis aqui só encurta o caminho da
 * requisição repetida. A decisão que não pode errar — um pedido por chave — é do
 * índice único em {@code orders}, então uma indisponibilidade do cache deixa o
 * checkout mais lento, nunca incorreto.
 */
@Repository
class RedisIdempotencyLock implements IdempotencyLock {
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyLock.class);
    private static final String PREFIX = "idempotency:";

    private final StringRedisTemplate redis;

    RedisIdempotencyLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean acquire(String scope, String key, String requestHash, Duration ttl) {
        try {
            return Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(redisKey(scope, key), requestHash, ttl));
        } catch (DataAccessException error) {
            log.warn("Guarda de idempotência indisponível; seguindo pelo índice único do banco");
            return true;
        }
    }

    @Override
    public Optional<String> requestHashOf(String scope, String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(redisKey(scope, key)));
        } catch (DataAccessException error) {
            return Optional.empty();
        }
    }

    private static String redisKey(String scope, String key) {
        return PREFIX + scope + ":" + key;
    }
}
