package com.paysi.payment.provider;

import java.util.Optional;
import java.util.UUID;

/** Subconta do provedor de pagamento associada a uma conta Paysi (vendedor ou afiliado), se houver. */
public interface WalletLookup {
    Optional<String> walletId(UUID accountId);
}
