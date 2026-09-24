package com.paysi.payment.inbox.port;

import java.util.Optional;
import java.util.UUID;

/** Resolve a cobrança interna a partir do identificador que o provedor usa para ela. */
public interface ChargeLookup {
    Optional<UUID> findByProviderChargeId(String providerChargeId);
}
