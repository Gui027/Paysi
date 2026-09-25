package com.paysi.affiliate.domain;

import java.util.UUID;

/** Configuração do programa de afiliados de um produto. */
public record AffiliateProgram(
        UUID productId,
        int commissionBps,
        AffiliationRecurrence recurrence,
        boolean autoApprove,
        String supportEmail,
        String description
) {
    public static final int DEFAULT_COMMISSION_BPS = 3_000;

    public static AffiliateProgram defaults(UUID productId) {
        return new AffiliateProgram(productId, DEFAULT_COMMISSION_BPS, AffiliationRecurrence.FIRST_CHARGE,
                false, null, null);
    }
}
