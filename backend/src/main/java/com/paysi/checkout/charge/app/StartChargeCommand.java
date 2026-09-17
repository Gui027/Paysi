package com.paysi.checkout.charge.app;

import java.time.Instant;

public record StartChargeCommand(String cardToken, String ip, String userAgent, String deviceKey,
                                  String termsHash, Instant termsAcceptedAt) {
}
