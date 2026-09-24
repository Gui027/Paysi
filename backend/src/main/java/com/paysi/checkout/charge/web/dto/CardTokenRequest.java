package com.paysi.checkout.charge.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /v1/orders/{orderId}/card-token}. Nunca é logado (toString redigido). */
public record CardTokenRequest(
        @NotBlank @Size(max = 80) String holderName,
        @NotBlank @Pattern(regexp = "\\d{13,19}", message = "Número do cartão inválido") String number,
        @NotBlank @Pattern(regexp = "\\d{1,2}", message = "Mês de validade inválido") String expiryMonth,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "Ano de validade inválido") String expiryYear,
        @NotBlank @Pattern(regexp = "\\d{3,4}", message = "CVV inválido") String ccv,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "CEP inválido") String postalCode,
        @NotBlank @Size(max = 10) String addressNumber,
        @NotBlank @Pattern(regexp = "\\d{10,11}", message = "Telefone inválido") String phone
) {
    @Override
    public String toString() {
        return "CardTokenRequest[REDACTED]";
    }
}
