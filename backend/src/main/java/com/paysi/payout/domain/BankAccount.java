package com.paysi.payout.domain;

import java.time.Instant;
import java.util.UUID;

public record BankAccount(UUID id, UUID accountId, String bankCode, String branch, String numberLast4,
                          String holderTaxId, String holderType, String holderName, String accountType,
                          String pixKeyType, Instant verifiedAt, Instant archivedAt) {
    public boolean available() { return verifiedAt != null && archivedAt == null; }
}
