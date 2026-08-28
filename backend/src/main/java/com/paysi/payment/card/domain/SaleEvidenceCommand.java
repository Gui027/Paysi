package com.paysi.payment.card.domain;

import java.time.Instant;

public record SaleEvidenceCommand(String ip, String userAgent, String deviceKey,
                                  String termsHash, Instant termsAcceptedAt) {
    public SaleEvidenceCommand {
        if (termsHash == null || termsHash.isBlank() || termsAcceptedAt == null) {
            throw new IllegalArgumentException("Aceite dos termos é obrigatório");
        }
    }

    @Override
    public String toString() {
        return "SaleEvidenceCommand[REDACTED]";
    }
}
