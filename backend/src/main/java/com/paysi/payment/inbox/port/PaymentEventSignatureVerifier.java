package com.paysi.payment.inbox.port;

public interface PaymentEventSignatureVerifier {
    boolean valid(String provider, String payload, String signature);
}
