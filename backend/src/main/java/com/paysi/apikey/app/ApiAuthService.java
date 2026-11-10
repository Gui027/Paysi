package com.paysi.apikey.app;

import com.paysi.apikey.app.ApiKeyModels.AccessToken;
import com.paysi.apikey.app.ApiKeyModels.ApiPrincipal;
import com.paysi.apikey.app.ApiKeyModels.KeyCredentials;
import com.paysi.apikey.app.ApiKeyModels.TokenGrant;
import com.paysi.apikey.port.ApiKeyRepository;
import com.paysi.core.error.ForbiddenException;
import com.paysi.core.error.UnauthorizedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Autenticação da API pública. A chave Paysi pode ser usada diretamente como Bearer token; o fluxo opcional de
 * token curto por client credentials continua disponível para integrações que fazem rotação de credenciais.
 */
@Service
public class ApiAuthService {
    public static final long TOKEN_TTL_SECONDS = 3600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;
    private final Clock clock;

    @Autowired
    public ApiAuthService(ApiKeyRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ApiAuthService(ApiKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static UnauthorizedException invalidClient() {
        return new UnauthorizedException("API_INVALID_CLIENT", "client_id ou client_secret inválido");
    }

    @Transactional
    public AccessToken issue(String clientId, String clientSecret) {
        UUID parsed;
        try {
            parsed = UUID.fromString(clientId == null ? "" : clientId.strip());
        } catch (IllegalArgumentException error) {
            throw invalidClient();
        }
        KeyCredentials key = repository.findByClientId(parsed).orElseThrow(ApiAuthService::invalidClient);
        byte[] expected = key.secretHash().getBytes(StandardCharsets.UTF_8);
        byte[] given = hash(clientSecret == null ? "" : clientSecret.strip()).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, given)) throw invalidClient();

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = "pay_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.purgeExpiredTokens(now);
        repository.insertToken(hash(token), key.id(), now.plusSeconds(TOKEN_TTL_SECONDS));
        repository.touch(key.id());
        return new AccessToken(token, "Bearer", TOKEN_TTL_SECONDS, String.join(" ", key.scopes()));
    }

    /**
     * Valida o cabeçalho Authorization ("Bearer …"), o escopo exigido ({@code null} = qualquer chave) e, se informado,
     * o cabeçalho X-Paysi-Account-Id.
     */
    @Transactional
    public ApiPrincipal authenticate(String authorization, String accountHeader, String requiredScope) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7) || authorization.length() <= 7) {
            throw new UnauthorizedException("API_UNAUTHENTICATED", "Informe o token no cabeçalho Authorization: Bearer <token>");
        }
        String bearer = authorization.substring(7).strip();
        TokenGrant grant = bearer.startsWith("paysi_live_") ? grantFromApiKey(bearer) : repository.findToken(hash(bearer))
                .orElseThrow(() -> new UnauthorizedException("API_TOKEN_INVALID", "Token ou chave de API inválido"));
        if (!grant.expiresAt().isAfter(clock.instant())) {
            throw new UnauthorizedException("API_TOKEN_INVALID", "Token inválido ou expirado");
        }
        if (accountHeader != null && !accountHeader.isBlank() && !accountHeader.strip().equalsIgnoreCase(grant.accountId().toString())) {
            throw new ForbiddenException("API_ACCOUNT_MISMATCH", "O X-Paysi-Account-Id não pertence a esta API Key");
        }
        if (requiredScope != null && !grant.scopes().contains(requiredScope)) {
            throw new ForbiddenException("API_SCOPE_MISSING", "Esta API Key não tem permissão para o endpoint \"" + requiredScope + "\"");
        }
        repository.touch(grant.keyId());
        return new ApiPrincipal(grant.accountId(), grant.keyId(), grant.scopes());
    }

    private TokenGrant grantFromApiKey(String apiKey) {
        String[] parts = apiKey.split("_", 4);
        if (parts.length != 4 || parts[2].length() != 32) {
            throw new UnauthorizedException("API_KEY_INVALID", "Chave de API inválida");
        }
        UUID clientId;
        try {
            String value = parts[2];
            clientId = UUID.fromString(value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                    + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20));
        } catch (RuntimeException error) {
            throw new UnauthorizedException("API_KEY_INVALID", "Chave de API inválida");
        }
        KeyCredentials key = repository.findByClientId(clientId)
                .orElseThrow(() -> new UnauthorizedException("API_KEY_INVALID", "Chave de API inválida"));
        byte[] expected = key.secretHash().getBytes(StandardCharsets.UTF_8);
        byte[] given = hash(apiKey).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, given)) {
            throw new UnauthorizedException("API_KEY_INVALID", "Chave de API inválida");
        }
        return new TokenGrant(key.id(), key.accountId(), key.scopes(), Instant.MAX);
    }
}
