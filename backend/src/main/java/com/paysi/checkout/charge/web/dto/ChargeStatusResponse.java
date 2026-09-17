package com.paysi.checkout.charge.web.dto;

import com.paysi.checkout.charge.port.ChargeCreationRepository.ChargeView;

import java.time.Instant;
import java.util.UUID;

public record ChargeStatusResponse(
        UUID chargeId,
        String method,
        String status,
        String boletoBarcode,
        String boletoUrl,
        String pixQrCode,
        Instant expiresAt
) {
    public static ChargeStatusResponse from(ChargeView view) {
        return new ChargeStatusResponse(view.chargeId(), view.method(), view.status(), view.boletoBarcode(),
                view.boletoUrl(), view.pixQrCode(), view.expiresAt());
    }
}
