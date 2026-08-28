package com.paysi.payout.port;

import java.util.UUID;

public interface PayoutProvider {
    TransferResult requestPix(UUID payoutId, long amountCents, UUID bankAccountId);
    record TransferResult(String providerTransferId, String receiptUrl) { }
}
