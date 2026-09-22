package com.paysi.payment.inbox.adapter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacPaymentEventSignatureVerifierTest {
    @Test
    void asaasUsesPlainTokenCompareNotHmac() {
        var verifier = new HmacPaymentEventSignatureVerifier("dev-payment-webhook-secret", "asaas-secret-token");

        assertThat(verifier.valid("asaas", "{\"anything\":true}", "asaas-secret-token")).isTrue();
        assertThat(verifier.valid("asaas", "{\"anything\":true}", "wrong-token")).isFalse();
    }

    @Test
    void asaasWithoutConfiguredTokenAlwaysRejects() {
        var verifier = new HmacPaymentEventSignatureVerifier("dev-payment-webhook-secret", "");
        assertThat(verifier.valid("asaas", "{}", "anything")).isFalse();
    }

    @Test
    void otherProvidersStillUseHmac() {
        var verifier = new HmacPaymentEventSignatureVerifier("dev-payment-webhook-secret", "asaas-secret-token");
        assertThat(verifier.valid("fake", "payload", "asaas-secret-token")).isFalse();
        assertThat(verifier.valid("fake", "payload", "not-hex")).isFalse();
    }
}
