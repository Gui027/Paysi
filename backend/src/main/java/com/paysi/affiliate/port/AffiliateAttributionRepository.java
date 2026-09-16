package com.paysi.affiliate.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AffiliateAttributionRepository {

    /** Afiliação APROVADA do afiliado para o produto, se existir (não precisa ter clique ainda). */
    Optional<UUID> findApprovedAffiliation(UUID productId, UUID affiliateId);

    void recordClick(UUID id, UUID affiliationId, UUID productId, String visitorKey, String ip,
                      Instant now, Instant expiresAt);

    /**
     * Último clique não expirado do visitante para o produto (atribuição de última origem),
     * desde que a afiliação continue APROVADA.
     */
    Optional<Attribution> resolveAttribution(UUID productId, String visitorKey, Instant now);

    record Attribution(UUID affiliationId, UUID affiliateId, int commissionBps, boolean allCycles) {
    }
}
