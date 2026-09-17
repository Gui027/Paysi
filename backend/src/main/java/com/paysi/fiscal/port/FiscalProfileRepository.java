package com.paysi.fiscal.port;

import com.paysi.fiscal.domain.FiscalProfile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface FiscalProfileRepository {
    Optional<FiscalProfile> findByAccount(UUID accountId);

    /** Cria ou substitui o perfil, sempre zerando {@code validatedAt} (credencial mudou, precisa revalidar). */
    void upsert(UUID accountId, String municipalityCode, String municipalRegistration, String serviceItem,
                int taxBps, String taxRegime, String credentialRef);

    void markValidated(UUID accountId, Instant validatedAt);
}
