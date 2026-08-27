package com.paysi.payment.receivable.domain;

import java.time.Instant;

public record ProviderInstallment(int sequence, String providerId, Instant expectedAt, long amountCents) {
    public ProviderInstallment {
        if (sequence < 1) throw new IllegalArgumentException("A sequência deve ser positiva");
        if (providerId == null || providerId.isBlank()) throw new IllegalArgumentException("O ID do provedor é obrigatório");
        if (expectedAt == null) throw new IllegalArgumentException("A data esperada é obrigatória");
        if (amountCents <= 0) throw new IllegalArgumentException("O valor da parcela deve ser positivo");
    }
}
