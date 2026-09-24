package com.paysi.payment.provider.asaas.dto;

/** {@code expirationDate} vem como "yyyy-MM-dd HH:mm:ss" (não ISO-8601), por isso fica como texto. */
public record AsaasPixQrCodeResponse(String encodedImage, String payload, String expirationDate) {
}
