package com.paysi.collaborator.web;

import com.paysi.collaborator.app.CollaboratorModels.Collaborator;
import com.paysi.collaborator.app.CollaboratorModels.CollaboratorsPage;
import com.paysi.collaborator.app.CollaboratorService;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Colaboradores da conta do vendedor logado. */
@RestController
@Tag(name = "Colaboradores")
public class CollaboratorController {
    private static final String COOKIE_NAME = "paysi_session";

    public record CollaboratorRequest(String email, List<String> permissions) { }

    public record PermissionsRequest(List<String> permissions) { }

    private final CollaboratorService collaborators;
    private final SessionService sessions;

    public CollaboratorController(CollaboratorService collaborators, SessionService sessions) {
        this.collaborators = collaborators;
        this.sessions = sessions;
    }

    @GetMapping("/v1/collaborators")
    @Operation(summary = "Listar colaboradores, com busca por e-mail")
    public CollaboratorsPage list(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                  @RequestParam(name = "q", required = false) String query,
                                  @RequestParam(required = false) Integer page) {
        return collaborators.list(owner(token), query, page);
    }

    @PostMapping("/v1/collaborators")
    @Operation(summary = "Convidar um colaborador")
    public Collaborator add(@CookieValue(name = COOKIE_NAME, required = false) String token, @RequestBody CollaboratorRequest request) {
        return collaborators.add(owner(token), request.email(), request.permissions());
    }

    @PutMapping("/v1/collaborators/{id}")
    @Operation(summary = "Editar as permissões de um colaborador")
    public Collaborator update(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id,
                               @RequestBody PermissionsRequest request) {
        return collaborators.update(owner(token), id, request.permissions());
    }

    @PostMapping("/v1/collaborators/{id}/resend")
    @Operation(summary = "Reenviar o convite")
    public ResponseEntity<Void> resend(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        collaborators.resend(owner(token), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/v1/collaborators/{id}")
    @Operation(summary = "Remover o acesso de um colaborador")
    public ResponseEntity<Void> remove(@CookieValue(name = COOKIE_NAME, required = false) String token, @PathVariable UUID id) {
        collaborators.remove(owner(token), id);
        return ResponseEntity.noContent().build();
    }

    private UUID owner(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
