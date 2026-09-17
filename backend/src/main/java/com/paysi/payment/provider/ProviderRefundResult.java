package com.paysi.payment.provider;

public record ProviderRefundResult(String providerRefundId, boolean succeeded, String errorCode) {
    public ProviderRefundResult {
        if (succeeded && (providerRefundId == null || providerRefundId.isBlank())) {
            throw new IllegalArgumentException("Reembolso aprovado exige identificador do provedor");
        }
        if (!succeeded && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("Reembolso recusado exige código de erro");
        }
    }
}
