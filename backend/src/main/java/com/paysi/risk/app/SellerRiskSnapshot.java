package com.paysi.risk.app;

import java.util.UUID;

public record SellerRiskSnapshot(UUID accountId, int chargebackBps, int refundBps, String disputeAction,
                                  String refundAction) {
}
