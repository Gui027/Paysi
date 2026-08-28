package com.paysi.payment.provider;

public interface PaymentProvider {
    ProviderPaymentResult charge(ProviderPaymentRequest request);
}
