package com.paysi.identity.kyc.adapter;

import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.webhook.port.KycWebhookStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Substitui {@link ConfiguredKycProvider} em ambientes sem um provedor real de KYC
 * ligado (dev/staging: {@code paysi.kyc.provider=fake}). Não existe redirecionamento
 * real para o titular enviar documento — em vez disso a conta é aprovada na hora,
 * escrevendo direto em {@link KycWebhookStore} (o mesmo estado que
 * {@code KycWebhookService} aplicaria ao processar o webhook real do provedor).
 * Isso pula por completo a ida-e-volta assíncrona; é aceitável só porque não há
 * verificação de verdade acontecendo do outro lado.
 */
@Component
@ConditionalOnProperty(name = "paysi.kyc.provider", havingValue = "fake")
public class FakeKycProvider implements KycProvider {
    private final KycWebhookStore webhookStore;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public FakeKycProvider(KycWebhookStore webhookStore) {
        this(webhookStore, Clock.systemUTC());
    }

    FakeKycProvider(KycWebhookStore webhookStore, Clock clock) {
        this.webhookStore = webhookStore;
        this.clock = clock;
    }

    @Override
    public KycProcess createProcess(UUID accountId) {
        var requirements = List.of(
                new KycRequirement("IDENTITY_DOCUMENT", "Documento de identidade", "APPROVED", null, null),
                new KycRequirement("LIVENESS", "Prova de vida", "APPROVED", null, null));
        webhookStore.apply(accountId, KycStatus.APPROVED, ensureSubaccount(accountId), requirements);
        return new KycProcess("fake_" + accountId, "https://fake.paysi/kyc/" + accountId,
                clock.instant().plus(Duration.ofHours(24)), requirements);
    }

    @Override
    public String ensureSubaccount(UUID accountId) {
        return "fake_sub_" + accountId;
    }
}
