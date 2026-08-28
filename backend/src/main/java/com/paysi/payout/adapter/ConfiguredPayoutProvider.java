package com.paysi.payout.adapter;

import com.paysi.payout.port.PayoutProvider;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class ConfiguredPayoutProvider implements PayoutProvider {
    @Override public TransferResult requestPix(UUID payoutId, long amountCents, UUID bankAccountId) {
        return new TransferResult("transfer_" + payoutId, "https://paysi.local/receipts/" + payoutId);
    }
}
