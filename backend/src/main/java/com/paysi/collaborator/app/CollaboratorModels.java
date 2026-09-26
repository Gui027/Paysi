package com.paysi.collaborator.app;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos da tela Colaboradores. {@code ALL} é o "Acesso total"; senão vale a lista de áreas liberadas. */
public final class CollaboratorModels {
    private CollaboratorModels() { }

    public static final String ALL = "ALL";
    public static final Set<String> AREAS = Set.of("products", "sales", "subscriptions", "finance", "reports", "affiliates", "integrations");

    public record Collaborator(UUID id, String email, String status, List<String> permissions, Instant invitedAt) { }

    public record CollaboratorsPage(List<Collaborator> items, int page, int size, long total, int totalPages) { }
}
