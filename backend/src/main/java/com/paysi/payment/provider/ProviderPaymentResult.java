package com.paysi.payment.provider;

import java.util.List;

public record ProviderPaymentResult(String providerChargeId, ProviderChargeStatus status,
                                    ProviderPaymentData paymentData, long providerFeeCents,
                                    List<ProviderReceivable> receivables, ProviderThreeDs threeDs,
                                    String errorCode, boolean retryable) {
    public ProviderPaymentResult {
        if (providerChargeId == null || providerChargeId.isBlank() || status == null || threeDs == null) {
            throw new IllegalArgumentException("Identificador e status do provedor são obrigatórios");
        }
        if (providerFeeCents < 0) throw new IllegalArgumentException("Taxa do provedor não pode ser negativa");
        if (status == ProviderChargeStatus.ERROR && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("Erro do provedor exige código estável");
        }
        receivables = receivables == null ? List.of() : List.copyOf(receivables);
    }
}
