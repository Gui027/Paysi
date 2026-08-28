package com.paysi.payment.provider;

import java.time.Instant;

public record ProviderReceivable(int sequence, String providerId, Instant expectedAt, long amountCents) {
    public ProviderReceivable {
        if (sequence < 1 || providerId == null || providerId.isBlank()
                || expectedAt == null || amountCents <= 0) {
            throw new IllegalArgumentException("Recebível do provedor inválido");
        }
    }
}
