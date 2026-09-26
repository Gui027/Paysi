package com.paysi.apikey.port;

import com.paysi.apikey.app.ApiKeyModels.ApiKey;
import com.paysi.apikey.app.ApiKeyModels.KeyCredentials;
import com.paysi.apikey.app.ApiKeyModels.TokenGrant;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository {
    long count(UUID accountId);

    void insert(UUID id, UUID accountId, String name, UUID clientId, String secretHash, String last4, List<String> scopes);

    List<ApiKey> list(UUID accountId, String query);

    Optional<ApiKey> find(UUID accountId, UUID id);

    void update(UUID accountId, UUID id, String name, List<String> scopes);

    void delete(UUID accountId, UUID id);

    Optional<KeyCredentials> findByClientId(UUID clientId);

    void insertToken(String tokenHash, UUID keyId, Instant expiresAt);

    Optional<TokenGrant> findToken(String tokenHash);

    /** Marca o uso, no máximo uma vez por minuto, para não escrever a cada chamada. */
    void touch(UUID keyId);

    void purgeExpiredTokens(Instant now);
}
