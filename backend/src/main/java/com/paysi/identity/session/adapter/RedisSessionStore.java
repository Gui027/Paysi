package com.paysi.identity.session.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.identity.session.domain.UserSession;
import com.paysi.identity.session.port.SessionStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
class RedisSessionStore implements SessionStore {
    private static final String SESSION_PREFIX = "session:";
    private static final String ACCOUNT_PREFIX = "account-sessions:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    RedisSessionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(String tokenHash, UserSession session) {
        Duration ttl = Duration.between(Instant.now(), session.expiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            delete(tokenHash);
            return;
        }
        try {
            redis.opsForValue().set(sessionKey(tokenHash), objectMapper.writeValueAsString(session), ttl);
            String accountKey = accountKey(session.accountId());
            redis.opsForSet().add(accountKey, tokenHash);
            redis.expire(accountKey, ttl.plusMinutes(5));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar sessão", ex);
        }
    }

    @Override
    public Optional<UserSession> find(String tokenHash) {
        String json = redis.opsForValue().get(sessionKey(tokenHash));
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, UserSession.class));
        } catch (JsonProcessingException ex) {
            redis.delete(sessionKey(tokenHash));
            return Optional.empty();
        }
    }

    @Override
    public void delete(String tokenHash) {
        find(tokenHash).ifPresent(session -> redis.opsForSet().remove(accountKey(session.accountId()), tokenHash));
        redis.delete(sessionKey(tokenHash));
    }

    @Override
    public void deleteAllForAccount(UUID accountId) {
        String accountKey = accountKey(accountId);
        Set<String> hashes = redis.opsForSet().members(accountKey);
        if (hashes != null && !hashes.isEmpty()) {
            redis.delete(hashes.stream().map(RedisSessionStore::sessionKey).toList());
        }
        redis.delete(accountKey);
    }

    private static String sessionKey(String hash) {
        return SESSION_PREFIX + hash;
    }

    private static String accountKey(UUID accountId) {
        return ACCOUNT_PREFIX + accountId;
    }
}
