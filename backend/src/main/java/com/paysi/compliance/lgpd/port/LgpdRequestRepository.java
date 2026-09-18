package com.paysi.compliance.lgpd.port;

import com.paysi.compliance.lgpd.domain.LgpdRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LgpdRequestRepository {

    LgpdRequest insert(UUID id, String subjectKind, String subjectRef, String kind, Instant dueAt,
                        Instant createdAt);

    /** Trava a linha para atribuição/resolução — mesma corrida de dois operadores que o admin já resolve. */
    Optional<LgpdRequest> lockForUpdate(UUID id);

    void assign(UUID id, UUID assigneeId, String status);

    void resolve(UUID id, String status, String resolution);
}
