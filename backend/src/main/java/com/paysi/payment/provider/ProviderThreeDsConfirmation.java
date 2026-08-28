package com.paysi.payment.provider;

import java.util.UUID;

public record ProviderThreeDsConfirmation(UUID orderId, String providerChargeId, String challengeToken) {
    public ProviderThreeDsConfirmation {
        if (orderId == null || blank(providerChargeId) || blank(challengeToken)) {
            throw new IllegalArgumentException("Confirmação 3DS incompleta");
        }
    }

    @Override
    public String toString() {
        return "ProviderThreeDsConfirmation[orderId=" + orderId + ", providerChargeId="
                + providerChargeId + ", challengeToken=[REDACTED]]";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
