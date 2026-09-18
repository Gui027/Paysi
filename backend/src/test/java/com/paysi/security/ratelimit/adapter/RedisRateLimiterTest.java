package com.paysi.security.ratelimit.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisRateLimiterTest {

    @Test
    void allowsUntilLimitAndBlocksAfter() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("ratelimit:checkout:order:ip:1.2.3.4")).thenReturn(1L, 2L, 3L);

        var limiter = new RedisRateLimiter(redis);

        assertThat(limiter.allow("checkout:order:ip:1.2.3.4", 2, Duration.ofSeconds(60))).isTrue();
        assertThat(limiter.allow("checkout:order:ip:1.2.3.4", 2, Duration.ofSeconds(60))).isTrue();
        assertThat(limiter.allow("checkout:order:ip:1.2.3.4", 2, Duration.ofSeconds(60))).isFalse();
    }

    @Test
    void setsExpiryOnlyOnFirstIncrement() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment("ratelimit:k")).thenReturn(1L);

        new RedisRateLimiter(redis).allow("k", 5, Duration.ofSeconds(30));

        verify(redis).expire("ratelimit:k", Duration.ofSeconds(30));
    }

    @Test
    void degradesToAllowWhenRedisIsUnavailable() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new QueryTimeoutException("indisponível"));

        boolean allowed = new RedisRateLimiter(redis).allow("k", 1, Duration.ofSeconds(30));

        assertThat(allowed).isTrue();
    }
}
