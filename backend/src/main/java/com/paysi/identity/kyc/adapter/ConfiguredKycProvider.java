package com.paysi.identity.kyc.adapter;

import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class ConfiguredKycProvider implements KycProvider {
    private final String baseUrl;
    private final Clock clock = Clock.systemUTC();

    public ConfiguredKycProvider(@Value("${paysi.kyc.provider-base-url:https://kyc.local/process}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    @Override
    public KycProcess createProcess(UUID accountId) {
        String processId = UUID.randomUUID().toString();
        return new KycProcess(processId, baseUrl + "/" + processId, clock.instant().plus(Duration.ofHours(24)),
                List.of(new KycRequirement("IDENTITY_DOCUMENT", "Documento de identidade", "PENDING", null, null),
                        new KycRequirement("LIVENESS", "Prova de vida", "PENDING", null, null)));
    }
}
