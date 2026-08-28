package com.paysi.payment.provider;

import java.time.Instant;

public record ProviderPaymentData(String pixQrCode, String boletoBarcode, String boletoUrl,
                                  Instant expiresAt) {}
