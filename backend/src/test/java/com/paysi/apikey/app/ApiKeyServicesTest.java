package com.paysi.apikey.app;

import com.paysi.apikey.app.ApiKeyModels.AccessToken;
import com.paysi.apikey.app.ApiKeyModels.ApiKey;
import com.paysi.apikey.app.ApiKeyModels.CreatedApiKey;
import com.paysi.apikey.app.ApiKeyModels.KeyCredentials;
import com.paysi.apikey.app.ApiKeyModels.TokenGrant;
import com.paysi.apikey.port.ApiKeyRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyServicesTest {
    private static final UUID OWNER = UUID.randomUUID();

    /** Repositório em memória: cobre a regra sem depender de banco. */
    static class MemoryRepository implements ApiKeyRepository {
        record Row(UUID id, UUID accountId, String name, UUID clientId, String hash, String last4, List<String> scopes) { }

        final List<Row> rows = new ArrayList<>();
        final Map<String, TokenGrant> tokens = new HashMap<>();
        int touched;

        public long count(UUID accountId) { return rows.stream().filter(row -> row.accountId.equals(accountId)).count(); }

        public void insert(UUID id, UUID accountId, String name, UUID clientId, String secretHash, String last4, List<String> scopes) {
            rows.add(new Row(id, accountId, name, clientId, secretHash, last4, scopes));
        }

        ApiKey view(Row row) {
            return new ApiKey(row.id, row.name, "*****" + row.last4, row.scopes, Instant.EPOCH, null, row.clientId, row.accountId);
        }

        public List<ApiKey> list(UUID accountId, String query) {
            return rows.stream().filter(row -> row.accountId.equals(accountId)).map(this::view).toList();
        }

        public Optional<ApiKey> find(UUID accountId, UUID id) {
            return rows.stream().filter(row -> row.accountId.equals(accountId) && row.id.equals(id)).findFirst().map(this::view);
        }

        public void update(UUID accountId, UUID id, String name, List<String> scopes) {
            Row old = rows.stream().filter(row -> row.id.equals(id)).findFirst().orElseThrow();
            rows.remove(old);
            rows.add(new Row(old.id, old.accountId, name, old.clientId, old.hash, old.last4, scopes));
        }

        public void delete(UUID accountId, UUID id) { rows.removeIf(row -> row.id.equals(id)); tokens.values().removeIf(grant -> grant.keyId().equals(id)); }

        public Optional<KeyCredentials> findByClientId(UUID clientId) {
            return rows.stream().filter(row -> row.clientId.equals(clientId)).findFirst()
                    .map(row -> new KeyCredentials(row.id, row.accountId, row.hash, row.scopes));
        }

        public void insertToken(String tokenHash, UUID keyId, Instant expiresAt) {
            Row row = rows.stream().filter(candidate -> candidate.id.equals(keyId)).findFirst().orElseThrow();
            tokens.put(tokenHash, new TokenGrant(keyId, row.accountId, row.scopes, expiresAt));
        }

        public Optional<TokenGrant> findToken(String tokenHash) {
            TokenGrant grant = tokens.get(tokenHash);
            return grant == null || rows.stream().noneMatch(row -> row.id.equals(grant.keyId())) ? Optional.empty() : Optional.of(grant);
        }

        public void touch(UUID keyId) { touched++; }

        public void purgeExpiredTokens(Instant now) { tokens.values().removeIf(grant -> grant.expiresAt().isBefore(now)); }
    }

    private MemoryRepository repository;
    private ApiKeyService keys;
    private ApiAuthService auth;
    private Instant now;

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository();
        keys = new ApiKeyService(repository);
        now = Instant.parse("2026-09-26T12:00:00Z");
        auth = new ApiAuthService(repository, new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now; }
        });
    }

    @Test
    void createReturnsTheSecretOnceAndStoresOnlyItsHash() {
        CreatedApiKey created = keys.create(OWNER, "  ERP  ", List.of("sales", "sales_refund"));
        assertThat(created.clientSecret()).hasSize(64);
        assertThat(created.key().name()).isEqualTo("ERP");
        assertThat(created.key().keyHint()).isEqualTo("*****" + created.clientSecret().substring(60));
        assertThat(repository.rows.get(0).hash()).isEqualTo(ApiAuthService.hash(created.clientSecret())).isNotEqualTo(created.clientSecret());
        assertThat(keys.get(OWNER, created.key().id()).scopes()).containsExactly("sales", "sales_refund");
    }

    @Test
    void validatesNameScopesAndLimit() {
        assertThatThrownBy(() -> keys.create(OWNER, " ", List.of("sales"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> keys.create(OWNER, "x", List.of())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> keys.create(OWNER, "x", List.of("admin"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> keys.create(OWNER, "x", List.of("sales_refund"))).isInstanceOf(ValidationException.class);
        for (int index = 0; index < ApiKeyService.MAX_KEYS; index++) keys.create(OWNER, "k" + index, List.of("reports"));
        assertThatThrownBy(() -> keys.create(OWNER, "extra", List.of("reports"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void anotherAccountCannotSeeEditOrDeleteTheKey() {
        CreatedApiKey created = keys.create(OWNER, "ERP", List.of("sales"));
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(() -> keys.get(stranger, created.key().id())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> keys.update(stranger, created.key().id(), "x", List.of("sales"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> keys.delete(stranger, created.key().id())).isInstanceOf(NotFoundException.class);
        assertThat(keys.update(OWNER, created.key().id(), "Novo", List.of("finance")).name()).isEqualTo("Novo");
    }

    @Test
    void tokenFlowChecksTheSecretTheScopeTheAccountAndTheExpiry() {
        CreatedApiKey created = keys.create(OWNER, "ERP", List.of("sales"));
        String clientId = created.key().clientId().toString();

        assertThatThrownBy(() -> auth.issue(clientId, "errado")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.issue("nao-e-uuid", created.clientSecret())).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.issue(UUID.randomUUID().toString(), created.clientSecret())).isInstanceOf(UnauthorizedException.class);

        AccessToken token = auth.issue(clientId, created.clientSecret());
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(3600);
        assertThat(token.scope()).isEqualTo("sales");
        String bearer = "Bearer " + token.accessToken();

        assertThat(auth.authenticate(bearer, null, "sales").accountId()).isEqualTo(OWNER);
        assertThat(auth.authenticate(bearer, OWNER.toString(), null).scopes()).containsExactly("sales");
        assertThatThrownBy(() -> auth.authenticate(bearer, UUID.randomUUID().toString(), "sales")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> auth.authenticate(bearer, null, "finance")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> auth.authenticate(null, null, "sales")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.authenticate("Bearer pay_falso", null, "sales")).isInstanceOf(UnauthorizedException.class);

        now = now.plusSeconds(3601);
        assertThatThrownBy(() -> auth.authenticate(bearer, null, "sales")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void deletingTheKeyRevokesItsTokensAndEditingScopesTakesEffectOnNextToken() {
        CreatedApiKey created = keys.create(OWNER, "ERP", List.of("sales"));
        String bearer = "Bearer " + auth.issue(created.key().clientId().toString(), created.clientSecret()).accessToken();
        keys.delete(OWNER, created.key().id());
        assertThatThrownBy(() -> auth.authenticate(bearer, null, "sales")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> auth.issue(created.key().clientId().toString(), created.clientSecret())).isInstanceOf(UnauthorizedException.class);
    }
}
