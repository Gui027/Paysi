package com.paysi.payment.inbox.domain;

import java.time.Instant;
import java.util.UUID;

public record ProviderEventPayload(String providerEventId, String eventType, UUID chargeId,
                                   String providerChargeId, Instant occurredAt) {
    public ProviderEventPayload {
        if (blank(providerEventId) || blank(eventType) || chargeId == null
                || blank(providerChargeId) || occurredAt == null) {
            throw new IllegalArgumentException("Evento do provedor incompleto");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
