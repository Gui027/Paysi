package com.paysi.risk.app;

public record PlatformRiskSnapshot(int chargebackBps, int refundBps, boolean alerted, boolean frozen) {
}
