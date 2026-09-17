package com.paysi.checkout.charge.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ChargeStartRequest(
        String cardToken,
        String deviceKey,
        @NotBlank String termsHash,
        @NotNull Instant termsAcceptedAt
) {
}
