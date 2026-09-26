package com.paysi.apikey.app;

import com.paysi.apikey.app.ApiKeyModels.ApiKey;
import com.paysi.apikey.app.ApiKeyModels.CreatedApiKey;
import com.paysi.apikey.port.ApiKeyRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Gestão das chaves de API do vendedor (tela Apps → API). */
@Service
public class ApiKeyService {
    public static final int MAX_KEYS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(UUID accountId, String query) {
        return repository.list(accountId, query);
    }

    @Transactional(readOnly = true)
    public ApiKey get(UUID accountId, UUID id) {
        return repository.find(accountId, id).orElseThrow(() -> new NotFoundException("API_KEY_NOT_FOUND", "API Key não encontrada"));
    }

    /** Cria a chave e devolve o segredo uma única vez: no banco fica só o hash. */
    @Transactional
    public CreatedApiKey create(UUID accountId, String rawName, List<String> scopes) {
        String name = name(rawName);
        List<String> granted = scopes(scopes);
        if (repository.count(accountId) >= MAX_KEYS) {
            throw new ConflictException("API_KEY_LIMIT", "Você atingiu o limite de " + MAX_KEYS + " API Keys. Exclua uma para criar outra", null);
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = HexFormat.of().formatHex(bytes);
        UUID id = UUID.randomUUID();
        repository.insert(id, accountId, name, UUID.randomUUID(), ApiAuthService.hash(secret), secret.substring(secret.length() - 4), granted);
        return new CreatedApiKey(get(accountId, id), secret);
    }

    @Transactional
    public ApiKey update(UUID accountId, UUID id, String rawName, List<String> scopes) {
        get(accountId, id);
        repository.update(accountId, id, name(rawName), scopes(scopes));
        return get(accountId, id);
    }

    @Transactional
    public void delete(UUID accountId, UUID id) {
        get(accountId, id);
        repository.delete(accountId, id);
    }

    static String name(String value) {
        String name = value == null ? "" : value.strip();
        if (name.isEmpty() || name.length() > 60) {
            throw new ValidationException("API_KEY_NAME_INVALID", "Informe um nome de até 60 caracteres", "name");
        }
        return name;
    }

    /** Escolha ao menos um endpoint; "Reembolsar vendas" só faz sentido com "Vendas" marcado. */
    static List<String> scopes(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ValidationException("API_KEY_SCOPES_REQUIRED", "Escolha ao menos um endpoint", "scopes");
        }
        List<String> granted = new ArrayList<>();
        for (String scope : requested) {
            if (!ApiKeyModels.SCOPES.contains(scope)) {
                throw new ValidationException("API_KEY_SCOPE_INVALID", "Endpoint inválido", "scopes");
            }
            if (!granted.contains(scope)) granted.add(scope);
        }
        if (granted.contains(ApiKeyModels.SALES_REFUND) && !granted.contains(ApiKeyModels.SALES)) {
            throw new ValidationException("API_KEY_SCOPE_INVALID", "Para reembolsar vendas, marque também Vendas", "scopes");
        }
        return granted;
    }
}
