package com.paysi.payment.provider.asaas.dto;

import java.time.Instant;

public record AsaasPixQrCodeResponse(String encodedImage, String payload, Instant expirationDate) {
}
