package com.paysi.checkout.charge.web.dto;

import com.paysi.checkout.charge.app.ChargeStartResult;
import com.paysi.payment.card.domain.CardPaymentResult;

import java.time.Instant;
import java.util.UUID;

public record ChargeStartResponse(
        UUID chargeId,
        String method,
        String status,
        boolean idempotentReplay,
        CardThreeDsView threeDs,
        String boletoBarcode,
        String boletoUrl,
        String pixQrCode,
        Instant expiresAt
) {
    public static ChargeStartResponse from(ChargeStartResult result) {
        return switch (result.method()) {
            case "CARD" -> {
                var card = result.card();
                yield new ChargeStartResponse(result.chargeId(), "CARD", card.status(), card.idempotentReplay(),
                        new CardThreeDsView(card.threeDs().required(), card.threeDs().status(),
                                card.threeDs().challengeUrl()),
                        null, null, null, card.pixAlternativeExpiresAt());
            }
            case "BOLETO" -> {
                var boleto = result.boleto();
                yield new ChargeStartResponse(result.chargeId(), "BOLETO", boleto.status(),
                        boleto.idempotentReplay(), null, boleto.barcode(), boleto.pdfUrl(), null, boleto.dueAt());
            }
            case "PIX" -> {
                var pix = result.pix();
                yield new ChargeStartResponse(result.chargeId(), "PIX", pix.status(), pix.idempotentReplay(),
                        null, null, null, pix.qrCode(), pix.expiresAt());
            }
            default -> throw new IllegalStateException("Método de cobrança desconhecido: " + result.method());
        };
    }

    public static ChargeStartResponse fromCard(UUID chargeId, CardPaymentResult card) {
        return new ChargeStartResponse(chargeId, "CARD", card.status(), card.idempotentReplay(),
                new CardThreeDsView(card.threeDs().required(), card.threeDs().status(), card.threeDs().challengeUrl()),
                null, null, null, card.pixAlternativeExpiresAt());
    }

    public record CardThreeDsView(boolean required, String status, String challengeUrl) {
    }
}
