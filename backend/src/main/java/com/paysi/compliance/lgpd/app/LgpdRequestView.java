package com.paysi.compliance.lgpd.app;

import com.paysi.compliance.lgpd.domain.LgpdRequest;

import java.time.Instant;
import java.util.UUID;

public record LgpdRequestView(UUID id, String type, String subject, String status, Instant dueAt, UUID assignee,
                               String evidence) {
    public static LgpdRequestView from(LgpdRequest request) {
        return new LgpdRequestView(request.id(), request.kind(), request.subjectKind() + ":" + request.subjectRef(),
                request.status(), request.dueAt(), request.handledBy(), request.resolution());
    }
}
