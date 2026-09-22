package com.paysi.payment.inbox.adapter;

import com.paysi.payment.inbox.port.PaymentEventSignatureVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * A Asaas não assina o corpo do webhook — ela manda um token fixo (configurado no painel
 * dela, não a chave de API) no header {@code asaas-access-token}, comparado aqui por
 * igualdade simples. Provedores que assinam de verdade (hoje: {@code fake}, para
 * desenvolvimento/teste) continuam no esquema HMAC original.
 */
@Component
public class HmacPaymentEventSignatureVerifier implements PaymentEventSignatureVerifier {
    private final byte[] secret;
    private final byte[] asaasWebhookToken;

    public HmacPaymentEventSignatureVerifier(
            @Value("${paysi.payment.webhook-secret:dev-payment-webhook-secret}") String secret,
            @Value("${paysi.asaas.webhook-token:}") String asaasWebhookToken) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.asaasWebhookToken = asaasWebhookToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean valid(String provider, String payload, String signature) {
        if (provider == null || provider.isBlank() || signature == null) return false;
        if ("asaas".equals(provider)) return validAsaasToken(signature);
        return validHmac(provider, payload, signature);
    }

    private boolean validAsaasToken(String signature) {
        if (asaasWebhookToken.length == 0) return false;
        return MessageDigest.isEqual(asaasWebhookToken, signature.getBytes(StandardCharsets.UTF_8));
    }

    private boolean validHmac(String provider, String payload, String signature) {
        if (!signature.matches("[0-9a-fA-F]{64}") || secret.length == 0) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((provider + "." + payload).getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Falha ao validar assinatura do provedor", exception);
        }
    }
}
