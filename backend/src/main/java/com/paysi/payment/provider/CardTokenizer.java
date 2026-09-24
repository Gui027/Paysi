package com.paysi.payment.provider;

public interface CardTokenizer {
    /** @throws CardTokenizationException recusa do provedor (dados inválidos) ou indisponibilidade */
    CardTokenResult tokenize(CardTokenRequest request);
}
