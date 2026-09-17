package com.paysi.payment.provider;

import java.util.UUID;

public record ProviderRefundRequest(String providerChargeId, long amountCents, UUID refundId) {
    public ProviderRefundRequest {
        if (providerChargeId == null || providerChargeId.isBlank()) {
            throw new IllegalArgumentException("Identificador do provedor é obrigatório");
        }
        if (amountCents <= 0) throw new IllegalArgumentException("Valor do reembolso deve ser positivo");
        if (refundId == null) throw new IllegalArgumentException("Identificador do reembolso é obrigatório");
    }
}
