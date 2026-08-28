package com.paysi.payment.boleto.domain;

import java.time.Instant;

public record BoletoResult(String providerChargeId, String barcode, String pdfUrl,
                           Instant dueAt, String status, boolean idempotentReplay) {}
