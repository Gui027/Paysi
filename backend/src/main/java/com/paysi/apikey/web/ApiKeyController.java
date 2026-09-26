package com.paysi.apikey.web;

import com.paysi.apikey.app.ApiKeyModels.ApiKey;
import com.paysi.apikey.app.ApiKeyModels.CreatedApiKey;
import com.paysi.apikey.app.ApiKeyService;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tela Apps → API: o vendedor logado cria, edita e exclui as próprias chaves. */
@RestController
@Tag(name = "API Keys")
public class ApiKeyController {
    private static final String COOKIE_NAME = "paysi_session";

    public record ApiKeyRequest(String name, List<String> scopes) { }

    private final ApiKeyService keys;
    private final SessionService sessions;

    public ApiKeyController(ApiKeyService keys, SessionService sessions) {
        this.keys = keys;
        this.sessions = sessions;
    }

    @GetMapping("/v1/api-keys")
    @Operation(summary = "Listar as API Keys, com busca por nome")
    public List<ApiKey> list(@CookieValue(name = COOKIE_NAME, required = false) String token,
                             @RequestParam(name = "q", required = false) String query) {
        return keys.list(owner(token), query);
    }

    @GetMapping("/v1/api-keys/{id}")
    @Operation(summary = "Detalhar uma API Key (o client_secret nunca é devolvido)")
    public ApiKey get(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        return keys.get(owner(token), id);
    }

    @PostMapping("/v1/api-keys")
    @Operation(summary = "Criar uma API Key; o client_secret aparece só nesta resposta")
    public ResponseEntity<CreatedApiKey> create(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                                @RequestBody ApiKeyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(keys.create(owner(token), request.name(), request.scopes()));
    }

    @PutMapping("/v1/api-keys/{id}")
    @Operation(summary = "Editar nome e endpoints de uma API Key")
    public ApiKey update(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id,
                         @RequestBody ApiKeyRequest request) {
        return keys.update(owner(token), id, request.name(), request.scopes());
    }

    @DeleteMapping("/v1/api-keys/{id}")
    @Operation(summary = "Excluir uma API Key (os tokens dela deixam de valer na hora)")
    public ResponseEntity<Void> delete(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        keys.delete(owner(token), id);
        return ResponseEntity.noContent().build();
    }

    private UUID owner(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
