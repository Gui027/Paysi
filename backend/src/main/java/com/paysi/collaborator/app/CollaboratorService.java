package com.paysi.collaborator.app;

import com.paysi.collaborator.app.CollaboratorModels.Collaborator;
import com.paysi.collaborator.app.CollaboratorModels.CollaboratorsPage;
import com.paysi.collaborator.port.CollaboratorMailSender;
import com.paysi.collaborator.port.CollaboratorRepository;
import com.paysi.core.error.ConflictException;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class CollaboratorService {
    private static final Logger log = LoggerFactory.getLogger(CollaboratorService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final int PAGE_SIZE = 10;

    private final CollaboratorRepository repository;
    private final CollaboratorMailSender mail;

    public CollaboratorService(CollaboratorRepository repository, CollaboratorMailSender mail) {
        this.repository = repository;
        this.mail = mail;
    }

    @Transactional(readOnly = true)
    public CollaboratorsPage list(UUID ownerId, String query, Integer requestedPage) {
        String text = query == null || query.isBlank() ? null : query.strip();
        long total = repository.count(ownerId, text);
        int totalPages = (int) Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(1, Math.min(requestedPage == null ? 1 : requestedPage, totalPages));
        return new CollaboratorsPage(repository.list(ownerId, text, PAGE_SIZE, (page - 1) * PAGE_SIZE), page, PAGE_SIZE, total, totalPages);
    }

    @Transactional
    public Collaborator add(UUID ownerId, String rawEmail, List<String> permissions) {
        String email = normalizeEmail(rawEmail);
        List<String> granted = normalizePermissions(permissions);
        if (email.equalsIgnoreCase(repository.ownerEmail(ownerId)) || repository.exists(ownerId, email)) {
            throw new ConflictException("COLLABORATOR_ALREADY_EXISTS", "Esse usuário já é colaborador ou dono da conta", "email");
        }
        Collaborator created = repository.insert(ownerId, email, granted);
        send(ownerId, email);
        return created;
    }

    @Transactional
    public Collaborator update(UUID ownerId, UUID id, List<String> permissions) {
        find(ownerId, id);
        repository.updatePermissions(ownerId, id, normalizePermissions(permissions));
        return find(ownerId, id);
    }

    @Transactional
    public void resend(UUID ownerId, UUID id) {
        Collaborator found = find(ownerId, id);
        if (!"PENDING".equals(found.status())) {
            throw new ConflictException("COLLABORATOR_NOT_PENDING", "Este colaborador já aceitou o convite", null);
        }
        repository.touchInvite(ownerId, id);
        send(ownerId, found.email());
    }

    @Transactional
    public void remove(UUID ownerId, UUID id) {
        find(ownerId, id);
        repository.delete(ownerId, id);
    }

    private Collaborator find(UUID ownerId, UUID id) {
        return repository.find(ownerId, id).orElseThrow(() -> new NotFoundException("COLLABORATOR_NOT_FOUND", "Colaborador não encontrado"));
    }

    /** O convite já está registrado; se o e-mail falhar, o vendedor pode reenviar pelo menu da linha. */
    private void send(UUID ownerId, String email) {
        try {
            mail.sendInvite(email, repository.ownerName(ownerId));
        } catch (RuntimeException error) {
            log.warn("Falha ao enviar o convite de colaborador", error);
        }
    }

    static String normalizeEmail(String value) {
        String email = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new ValidationException("COLLABORATOR_EMAIL_INVALID", "Informe um e-mail válido", "email");
        }
        return email;
    }

    static List<String> normalizePermissions(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new ValidationException("COLLABORATOR_PERMISSIONS_REQUIRED", "Escolha ao menos uma permissão", "permissions");
        }
        if (requested.contains(CollaboratorModels.ALL)) return List.of(CollaboratorModels.ALL);
        List<String> granted = new ArrayList<>();
        for (String area : requested) {
            if (!CollaboratorModels.AREAS.contains(area)) {
                throw new ValidationException("COLLABORATOR_PERMISSION_INVALID", "Permissão inválida", "permissions");
            }
            if (!granted.contains(area)) granted.add(area);
        }
        return granted;
    }
}
