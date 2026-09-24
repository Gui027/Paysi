package com.paysi.payment.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "fake")
public class FakeCardTokenizer implements CardTokenizer {
    @Override
    public CardTokenResult tokenize(CardTokenRequest request) {
        String digits = request.number().replaceAll("\\D", "");
        return new CardTokenResult("tok_" + UUID.randomUUID(), "FAKE", digits.substring(Math.max(0, digits.length() - 4)));
    }
}
