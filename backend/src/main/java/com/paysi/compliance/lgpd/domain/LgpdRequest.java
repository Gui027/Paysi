package com.paysi.compliance.lgpd.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de titular sob a LGPD (RF-115). {@code subjectRef} é o identificador do
 * titular dentro do tipo declarado em {@code subjectKind} — hoje só {@code BUYER}
 * dispara anonimização (RNF-026); {@code ACCOUNT} fica registrado, mas fora do
 * escopo deste cartão.
 */
public record LgpdRequest(
        UUID id,
        String subjectKind,
        String subjectRef,
        String kind,
        String status,
        Instant dueAt,
        UUID handledBy,
        String resolution,
        Instant createdAt
) {
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String KIND_ACCESS = "ACCESS";
    public static final String KIND_DELETION = "DELETION";
    public static final String KIND_CORRECTION = "CORRECTION";
    public static final String KIND_PORTABILITY = "PORTABILITY";

    public static final String SUBJECT_BUYER = "BUYER";
    public static final String SUBJECT_ACCOUNT = "ACCOUNT";
}
