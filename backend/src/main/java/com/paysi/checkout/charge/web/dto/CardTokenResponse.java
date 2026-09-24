package com.paysi.checkout.charge.web.dto;

import com.paysi.payment.provider.CardTokenResult;

public record CardTokenResponse(String cardToken, String brand, String last4) {
    public static CardTokenResponse from(CardTokenResult result) {
        return new CardTokenResponse(result.token(), result.brand(), result.last4());
    }
}
