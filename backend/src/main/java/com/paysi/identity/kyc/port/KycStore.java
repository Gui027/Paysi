package com.paysi.identity.kyc.port;

import com.paysi.identity.kyc.domain.ComplianceProfile;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.domain.KycStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycStore {
    void lockAccount(UUID accountId);
    Optional<KycProcess> findProcess(UUID accountId);
    List<KycRequirement> requirements(UUID accountId);
    void saveStarted(UUID accountId, KycProcess process);

    /**
     * Só grava se a conta ainda não tinha subconta (coalesce) — nunca sobrescreve uma já existente.
     * {@code accessToken}: chave própria da subconta (ex.: Asaas), já vem em claro — o adaptador cuida
     * de criptografar antes de gravar. Pode ser nulo para provedores sem esse conceito.
     */
    void attachProviderAccount(UUID accountId, String providerAccountId, String accessToken);

    /** Chave própria da subconta, já decriptada. Vazio quando a conta ainda não tem subconta com chave salva. */
    Optional<String> decryptedAccessToken(UUID accountId);

    ComplianceProfile complianceProfile(UUID accountId);

    void saveComplianceProfile(UUID accountId, String postalCode, java.time.LocalDate birthDate, Long incomeValueCents);

    /** Apaga o processo/pendências guardados — próxima chamada a {@code start} chama o provedor de novo. */
    void clearProcess(UUID accountId);

    /** Atualiza o resultado obtido diretamente do provedor e substitui as pendências exibidas. */
    void updateStatus(UUID accountId, KycStatus status, List<KycRequirement> requirements);
}
