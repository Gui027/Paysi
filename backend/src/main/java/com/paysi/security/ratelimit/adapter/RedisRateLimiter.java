package com.paysi.security.ratelimit.adapter;

import com.paysi.security.ratelimit.port.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Janela fixa contada em Redis com {@code INCR} + {@code EXPIRE} na primeira
 * ocorrência da chave. Mesma tolerância a falha de {@code RedisIdempotencyLock}:
 * se o Redis estiver indisponível, a tentativa é liberada e o defeito vira
 * degradação, não indisponibilidade do checkout.
 */
@Component
public class RedisRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean allow(String key, int maxAttempts, Duration window) {
        try {
            String redisKey = PREFIX + key;
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) return true;
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= maxAttempts;
        } catch (DataAccessException error) {
            log.warn("Contador de limite de tentativas indisponível; requisição liberada");
            return true;
        }
    }
}
