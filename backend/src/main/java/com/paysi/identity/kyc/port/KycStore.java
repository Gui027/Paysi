package com.paysi.identity.kyc.port;

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
}
