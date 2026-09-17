package com.paysi.checkout.charge.app;

import com.paysi.payment.card.domain.CardPaymentResult;
import com.paysi.payment.boleto.domain.BoletoResult;
import com.paysi.payment.pix.domain.PixResult;

import java.util.UUID;

public record ChargeStartResult(UUID chargeId, String method, CardPaymentResult card, BoletoResult boleto,
                                 PixResult pix) {
    public static ChargeStartResult card(UUID chargeId, CardPaymentResult result) {
        return new ChargeStartResult(chargeId, "CARD", result, null, null);
    }

    public static ChargeStartResult boleto(UUID chargeId, BoletoResult result) {
        return new ChargeStartResult(chargeId, "BOLETO", null, result, null);
    }

    public static ChargeStartResult pix(UUID chargeId, PixResult result) {
        return new ChargeStartResult(chargeId, "PIX", null, null, result);
    }
}
