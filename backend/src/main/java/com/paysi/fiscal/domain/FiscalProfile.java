package com.paysi.fiscal.domain;

import java.time.Instant;
import java.util.UUID;

/** RF-095: dados de emissão coletados do vendedor, com a validação de homologação separada. */
public record FiscalProfile(UUID accountId, String municipalityCode, String municipalRegistration,
                             String serviceItem, int taxBps, String taxRegime, String credentialRef,
                             Instant validatedAt) {
    public boolean validated() {
        return validatedAt != null;
    }
}
