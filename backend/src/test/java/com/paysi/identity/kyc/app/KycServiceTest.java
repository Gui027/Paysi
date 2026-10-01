package com.paysi.identity.kyc.app;

import com.paysi.identity.domain.*;
import com.paysi.identity.kyc.domain.KycProcess;
import com.paysi.identity.kyc.domain.KycRequirement;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.port.KycStore;
import com.paysi.identity.port.AccountRepository;
import com.paysi.ledger.app.LedgerService;
import com.paysi.payment.provider.SubaccountProvider;
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

    @Test
    void savingTheComplianceProfileNormalizesThePostalCodeAndClearsTheCachedProcess() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.empty());

        fixture.service.saveComplianceProfile(ACCOUNT_ID, "01310-100", "1990-05-20", 150000L);

        verify(fixture.store).saveComplianceProfile(ACCOUNT_ID, "01310100", java.time.LocalDate.of(1990, 5, 20), 150000L);
        verify(fixture.store).clearProcess(ACCOUNT_ID);
    }

    @Test
    void rejectsAnIncompletePostalCodeAFutureBirthDateOrAMissingIncomeValue() {
        var fixture = fixture(KycStatus.PENDING, Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.saveComplianceProfile(ACCOUNT_ID, "123", "1990-05-20", 150000L))
                .isInstanceOf(com.paysi.core.error.ValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.saveComplianceProfile(ACCOUNT_ID, "01310-100", "2099-01-01", 150000L))
                .isInstanceOf(com.paysi.core.error.ValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.saveComplianceProfile(ACCOUNT_ID, "01310-100", "não-é-data", 150000L))
                .isInstanceOf(com.paysi.core.error.ValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.saveComplianceProfile(ACCOUNT_ID, "01310-100", "1990-05-20", null))
                .isInstanceOf(com.paysi.core.error.ValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.saveComplianceProfile(ACCOUNT_ID, "01310-100", "1990-05-20", -1L))
                .isInstanceOf(com.paysi.core.error.ValidationException.class);
        verify(fixture.store, never()).saveComplianceProfile(any(), any(), any(), any());
    }

    @Test
    void sendsTheDocumentWithTheTypeReturnedByTheProvider() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.plusSeconds(60))));
        when(fixture.store.decryptedAccessToken(ACCOUNT_ID)).thenReturn(Optional.of("sub-key"));
        when(fixture.subaccounts.pendingDocuments("sub-key")).thenReturn(List.of(
                new SubaccountProvider.PendingDocument("group-1", "PENDING", "IDENTIFICATION", "Documento", null)));

        fixture.service.submitDocument(ACCOUNT_ID, "group-1", new byte[]{1}, "rg.png", "image/png");

        verify(fixture.subaccounts).submitDocument(eq("sub-key"), eq("group-1"), eq("IDENTIFICATION"), any(byte[].class), eq("rg.png"), eq("image/png"));
    }

    @Test
    void refusesApiUploadWhenTheProviderRequiresOnboarding() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.plusSeconds(60))));
        when(fixture.store.decryptedAccessToken(ACCOUNT_ID)).thenReturn(Optional.of("sub-key"));
        when(fixture.subaccounts.pendingDocuments("sub-key")).thenReturn(List.of(
                new SubaccountProvider.PendingDocument("group-1", "PENDING", "IDENTIFICATION", "Utilize o link de onboarding", "https://asaas.example/onboarding")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                fixture.service.submitDocument(ACCOUNT_ID, "group-1", new byte[]{1}, "rg.png", "image/png"))
                .isInstanceOf(com.paysi.core.error.ConflictException.class)
                .hasMessageContaining("link seguro");
        verify(fixture.subaccounts, never()).submitDocument(any(), any(), any(), any(), any(), any());
    }

    @Test
    void doesNotHideAMissingSubaccountCredentialAsAnEmptyDocumentList() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.plusSeconds(60))));
        when(fixture.store.decryptedAccessToken(ACCOUNT_ID)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.pendingDocuments(ACCOUNT_ID))
                .isInstanceOf(com.paysi.core.error.ConflictException.class)
                .hasMessageContaining("suporte");
        verify(fixture.subaccounts, never()).pendingDocuments(any());
    }

    @Test
    void refreshesAnApprovedAsaasSubaccountAndChargesTheVerificationOnlyThroughTheIdempotentLedgerKey() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.plusSeconds(60))));
        when(fixture.store.decryptedAccessToken(ACCOUNT_ID)).thenReturn(Optional.of("sub-key"));
        when(fixture.subaccounts.accountStatus("sub-key")).thenReturn(
                new SubaccountProvider.SubaccountStatus("APPROVED", "APPROVED", "PENDING", "APPROVED"));

        var refreshed = fixture.service.refreshStatus(ACCOUNT_ID);

        assertThat(refreshed.kycStatus()).isEqualTo(KycStatus.APPROVED);
        assertThat(refreshed.requirements()).isEmpty();
        verify(fixture.store).updateStatus(ACCOUNT_ID, KycStatus.APPROVED, List.of());
        verify(fixture.ledger).write(argThat(command -> command.reference().id().equals(ACCOUNT_ID.toString())));
    }

    @Test
    void refreshesARejectedDocumentAsRejectedEvenWhenAsaasGeneralStatusIsPending() {
        var fixture = fixture(KycStatus.SUBMITTED, Optional.of(process(NOW.plusSeconds(60))));
        when(fixture.store.decryptedAccessToken(ACCOUNT_ID)).thenReturn(Optional.of("sub-key"));
        when(fixture.subaccounts.accountStatus("sub-key")).thenReturn(
                new SubaccountProvider.SubaccountStatus("PENDING", "APPROVED", "PENDING", "REJECTED"));

        var refreshed = fixture.service.refreshStatus(ACCOUNT_ID);

        assertThat(refreshed.kycStatus()).isEqualTo(KycStatus.REJECTED);
        verify(fixture.store).updateStatus(eq(ACCOUNT_ID), eq(KycStatus.REJECTED), anyList());
        verifyNoInteractions(fixture.ledger);
    }

    private static Fixture fixture(KycStatus status, Optional<KycProcess> existing) {
        AccountRepository accounts = mock(AccountRepository.class);
        KycStore store = mock(KycStore.class);
        KycProvider provider = mock(KycProvider.class);
        when(accounts.findById(ACCOUNT_ID)).thenReturn(Optional.of(account(status)));
        when(store.findProcess(ACCOUNT_ID)).thenReturn(existing);
        when(store.requirements(ACCOUNT_ID)).thenReturn(existing.map(KycProcess::requirements).orElse(List.of()));
        when(provider.createProcess(ACCOUNT_ID)).thenReturn(process(NOW.plusSeconds(3600)));
        var subaccounts = mock(SubaccountProvider.class);
        var ledger = mock(LedgerService.class);
        return new Fixture(new KycService(accounts, store, provider, subaccounts, ledger, Clock.fixed(NOW, ZoneOffset.UTC)), store, provider, subaccounts, ledger);
    }

    private static KycProcess process(Instant expiresAt) {
        return new KycProcess("provider-one", "https://provider/process/one", expiresAt,
                List.of(new KycRequirement("DOCUMENT", "Documento", "PENDING", "Envie uma imagem legível", NOW.plusSeconds(3600))));
    }

    private static Account account(KycStatus status) {
        return Account.reconstitute(ACCOUNT_ID, "user@example.com", "hash", "User", PersonType.PF,
                new TaxId("52998224725"), status, PayoutDelay.D32, 0, AccountStatus.ACTIVE, NOW.minusSeconds(100));
    }

    private record Fixture(KycService service, KycStore store, KycProvider provider, SubaccountProvider subaccounts,
                           LedgerService ledger) { }
}
