package com.paysi.payment.provider;

import java.util.UUID;

public record ProviderPaymentRequest(UUID orderId, long amountCents, ProviderPaymentMethod method,
                                     int installments, String paymentToken, ProviderBuyer buyer,
                                     ProviderSplit split, Integer boletoDueDays) {
    public ProviderPaymentRequest(UUID orderId, long amountCents, ProviderPaymentMethod method,
                                  int installments, String paymentToken, ProviderBuyer buyer,
                                  ProviderSplit split) {
        this(orderId, amountCents, method, installments, paymentToken, buyer, split, null);
    }

    public ProviderPaymentRequest {
        if (orderId == null || method == null || buyer == null || split == null) {
            throw new IllegalArgumentException("Pedido, método, comprador e split são obrigatórios");
        }
        if (amountCents <= 0) throw new IllegalArgumentException("Valor precisa ser positivo");
        if (installments < 1 || installments > 12) {
            throw new IllegalArgumentException("Parcelas precisam estar entre 1 e 12");
        }
        if (method != ProviderPaymentMethod.CARD && installments != 1) {
            throw new IllegalArgumentException("Apenas cartão aceita parcelamento");
        }
        if (method == ProviderPaymentMethod.CARD && (paymentToken == null || paymentToken.isBlank())) {
            throw new IllegalArgumentException("Cartão exige token do provedor");
        }
        if (method == ProviderPaymentMethod.BOLETO
                && (boletoDueDays == null || boletoDueDays < 1 || boletoDueDays > 15)) {
            throw new IllegalArgumentException("Vencimento do boleto precisa estar entre 1 e 15 dias");
        }
        if (method != ProviderPaymentMethod.BOLETO && boletoDueDays != null) {
            throw new IllegalArgumentException("Prazo de boleto só é aceito para boleto");
        }
        if (split.totalCents() != amountCents) {
            throw new IllegalArgumentException("Split precisa fechar no valor cobrado");
        }
    }

    @Override
    public String toString() {
        return "ProviderPaymentRequest[orderId=" + orderId + ", amountCents=" + amountCents
                + ", method=" + method + ", installments=" + installments
                + ", paymentToken=[REDACTED], buyer=[REDACTED], split=" + split
                + ", boletoDueDays=" + boletoDueDays + "]";
    }
}
