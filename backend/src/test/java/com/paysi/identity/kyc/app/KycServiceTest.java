package com.paysi.identity.kyc.app;

import com.paysi.identity.domain.*;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KycServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void startsKycOnlyWhenExplicitlyRequested() {
        var fixture = fixture(KycStatus.PENDING, Optional.empty());
        assertThat(fixture.service.current(ACCOUNT_ID).kycStatus()).isEqualTo(KycStatus.PENDING);
        verifyNoInteractions(fixture.provider);

        var started = fixture.service.start(ACCOUNT_ID);
        assertThat(started.providerUrl()).isEqualTo("https://provider/process/one");
        verify(fixture.store).saveStarted(eq(ACCOUNT_ID), any());
    }

    @Test
    void reusesActiveProcessAcrossRepeatedCalls() {
        var active = process(NOW.plusSeconds(60));
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(active));

        assertThat(fixture.service.start(ACCOUNT_ID).providerUrl()).isEqualTo(active.providerUrl());
        assertThat(fixture.service.start(ACCOUNT_ID).providerUrl()).isEqualTo(active.providerUrl());
        verifyNoInteractions(fixture.provider);
        verify(fixture.store, times(2)).lockAccount(ACCOUNT_ID);
    }

    @Test
    void createsNewProcessWhenPreviousLinkExpired() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.minusSeconds(1))));
        fixture.service.start(ACCOUNT_ID);
        verify(fixture.provider).createProcess(ACCOUNT_ID);
        verify(fixture.store).saveStarted(eq(ACCOUNT_ID), any());
    }

    private static Fixture fixture(KycStatus status, Optional<KycProcess> existing) {
        AccountRepository accounts = mock(AccountRepository.class);
        KycStore store = mock(KycStore.class);
        KycProvider provider = mock(KycProvider.class);
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(status)));
        when(store.findProcess(ACCOUNT_ID)).thenReturn(existing);
        when(store.requirements(ACCOUNT_ID)).thenReturn(existing.map(KycProcess::requirements).orElse(List.of()));
        when(provider.createProcess(ACCOUNT_ID)).thenReturn(process(NOW.plusSeconds(3600)));
        return new Fixture(new KycService(accounts, store, provider, Clock.fixed(NOW, ZoneOffset.UTC)), store, provider);
    }

    private static KycProcess process(Instant expiresAt) {
        return new KycProcess("provider-one", "https://provider/process/one", expiresAt,
                List.of(new KycRequirement("DOCUMENT", "Documento", "PENDING", "Envie uma imagem legível", NOW.plusSeconds(3600))));
    }

    private static Account account(KycStatus status) {
        return Account.reconstitute(ACCOUNT_ID, "user@example.com", "hash", "User", PersonType.PF,
                new TaxId("52998224725"), status, PayoutDelay.D32, 0, AccountStatus.ACTIVE, NOW.minusSeconds(100));
    }

    private record Fixture(KycService service, KycStore store, KycProvider provider) { }
}
