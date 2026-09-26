package com.paysi.apikey.adapter;

import com.paysi.apikey.app.ApiKeyModels.ApiKey;
import com.paysi.apikey.app.ApiKeyModels.KeyCredentials;
import com.paysi.apikey.app.ApiKeyModels.TokenGrant;
import com.paysi.apikey.port.ApiKeyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcApiKeyRepository implements ApiKeyRepository {
    private static final String COLUMNS = "id, name, secret_last4, scopes, created_at, last_used_at, client_id, account_id";
    private static final RowMapper<ApiKey> MAPPER = (rs, row) -> new ApiKey(rs.getObject("id", UUID.class), rs.getString("name"),
            "*****" + rs.getString("secret_last4"), List.of((String[]) rs.getArray("scopes").getArray()),
            rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("last_used_at")),
            rs.getObject("client_id", UUID.class), rs.getObject("account_id", UUID.class));

    private final JdbcTemplate jdbc;

    JdbcApiKeyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    /** Literal de array do Postgres; os escopos são valores fixos validados no serviço. */
    private static String array(List<String> values) {
        return "{" + String.join(",", values) + "}";
    }

    @Override
    public long count(UUID accountId) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM public_api_keys WHERE account_id = ?", Long.class, accountId);
        return total == null ? 0 : total;
    }

    @Override
    public void insert(UUID id, UUID accountId, String name, UUID clientId, String secretHash, String last4, List<String> scopes) {
        jdbc.update("INSERT INTO public_api_keys (id, account_id, name, client_id, secret_hash, secret_last4, scopes) VALUES (?, ?, ?, ?, ?, ?, ?::text[])",
                id, accountId, name, clientId, secretHash, last4, array(scopes));
    }

    @Override
    public List<ApiKey> list(UUID accountId, String query) {
        List<Object> params = new ArrayList<>(List.of(accountId));
        String filter = "";
        if (query != null && !query.isBlank()) {
            filter = " AND lower(name) LIKE ? ESCAPE '\\'";
            params.add("%" + query.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        return jdbc.query("SELECT " + COLUMNS + " FROM public_api_keys WHERE account_id = ?" + filter + " ORDER BY created_at DESC, id",
                MAPPER, params.toArray());
    }

    @Override
    public Optional<ApiKey> find(UUID accountId, UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM public_api_keys WHERE account_id = ? AND id = ?", MAPPER, accountId, id)
                .stream().findFirst();
    }

    @Override
    public void update(UUID accountId, UUID id, String name, List<String> scopes) {
        jdbc.update("UPDATE public_api_keys SET name = ?, scopes = ?::text[] WHERE account_id = ? AND id = ?", name, array(scopes), accountId, id);
    }

    @Override
    public void delete(UUID accountId, UUID id) {
        jdbc.update("DELETE FROM public_api_keys WHERE account_id = ? AND id = ?", accountId, id);
    }

    @Override
    public Optional<KeyCredentials> findByClientId(UUID clientId) {
        return jdbc.query("SELECT id, account_id, secret_hash, scopes FROM public_api_keys WHERE client_id = ?",
                (rs, row) -> new KeyCredentials(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                        rs.getString("secret_hash"), List.of((String[]) rs.getArray("scopes").getArray())), clientId)
                .stream().findFirst();
    }

    @Override
    public void insertToken(String tokenHash, UUID keyId, Instant expiresAt) {
        jdbc.update("INSERT INTO public_api_tokens (token_hash, api_key_id, expires_at) VALUES (?, ?, ?)",
                tokenHash, keyId, Timestamp.from(expiresAt));
    }

    @Override
    public Optional<TokenGrant> findToken(String tokenHash) {
        return jdbc.query("""
                SELECT k.id, k.account_id, k.scopes, t.expires_at
                  FROM public_api_tokens t JOIN public_api_keys k ON k.id = t.api_key_id
                 WHERE t.token_hash = ?
                """, (rs, row) -> new TokenGrant(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                List.of((String[]) rs.getArray("scopes").getArray()), rs.getTimestamp("expires_at").toInstant()), tokenHash)
                .stream().findFirst();
    }

    @Override
    public void touch(UUID keyId) {
        jdbc.update("UPDATE public_api_keys SET last_used_at = now() WHERE id = ? AND (last_used_at IS NULL OR last_used_at < now() - interval '1 minute')", keyId);
    }

    @Override
    public void purgeExpiredTokens(Instant now) {
        jdbc.update("DELETE FROM public_api_tokens WHERE expires_at < ?", Timestamp.from(now));
    }
}
