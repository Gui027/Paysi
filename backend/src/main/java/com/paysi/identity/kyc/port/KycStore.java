package com.paysi.identity.kyc.port;

import com.paysi.identity.kyc.domain.ComplianceProfile;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycStore {
    void lockAccount(UUID accountId);
    Optional<KycProcess> findProcess(UUID accountId);
    List<KycRequirement> requirements(UUID accountId);
    void saveStarted(UUID accountId, KycProcess process);

    /** Só grava se a conta ainda não tinha subconta (coalesce) — nunca sobrescreve uma já existente. */
    void attachProviderAccount(UUID accountId, String providerAccountId);

    ComplianceProfile complianceProfile(UUID accountId);

    void saveComplianceProfile(UUID accountId, String postalCode, java.time.LocalDate birthDate);

    /** Apaga o processo/pendências guardados — próxima chamada a {@code start} chama o provedor de novo. */
    void clearProcess(UUID accountId);
}
