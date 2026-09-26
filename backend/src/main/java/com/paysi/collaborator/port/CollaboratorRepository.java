package com.paysi.collaborator.port;

import com.paysi.collaborator.app.CollaboratorModels.Collaborator;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaboratorRepository {
    String ownerEmail(UUID accountId);

    String ownerName(UUID accountId);

    boolean exists(UUID accountId, String email);

    Collaborator insert(UUID accountId, String email, List<String> permissions);

    List<Collaborator> list(UUID accountId, String query, int limit, int offset);

    long count(UUID accountId, String query);

    Optional<Collaborator> find(UUID accountId, UUID id);

    void updatePermissions(UUID accountId, UUID id, List<String> permissions);

    void touchInvite(UUID accountId, UUID id);

    void delete(UUID accountId, UUID id);
}
