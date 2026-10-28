package com.paysi.identity.kyc.webhook.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.webhook.port.KycWebhookStore;
import com.paysi.identity.kyc.webhook.port.WebhookSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KycWebhookServiceTest {
    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String EVENT_ID = "event-one";
    private static final String PAYLOAD = """
            {"providerEventId":"event-one","accountReference":"11111111-1111-1111-1111-111111111111","status":"APPROVED","requirements":[]}
            """;

    @Test
    void rejectsInvalidSignatureBeforePersistingTheEvent() {
        var fixture = fixture(false, "RECEIVED", KycStatus.SUBMITTED);

        assertThatThrownBy(() -> fixture.service.handle(PAYLOAD, "invalid"))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(fixture.store, fixture.provider);
    }

    @Test
    void appliesRejectionWithoutCreatingSubaccount() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        String payload = PAYLOAD.replace("APPROVED", "REJECTED");

        var result = fixture.service.handle(payload, "valid");

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.store).apply(ACCOUNT_ID, KycStatus.REJECTED, null, java.util.List.of());
        verify(fixture.store).markProcessed(EVENT_ID);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void approvesAccountAndCreatesSubaccountWithoutChargingAnyFee() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        when(fixture.provider.ensureSubaccount(ACCOUNT_ID)).thenReturn("subaccount-one");

        var result = fixture.service.handle(PAYLOAD, "valid");

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.store).apply(ACCOUNT_ID, KycStatus.APPROVED, "subaccount-one", java.util.List.of());
        verify(fixture.store).markProcessed(EVENT_ID);
    }

    @Test
    void treatsProcessedEventAsIdempotentReplay() {
        var fixture = fixture(true, "PROCESSED", KycStatus.SUBMITTED);

        assertThat(fixture.service.handle(PAYLOAD, "valid").idempotentReplay()).isTrue();

        verify(fixture.store).receive(EVENT_ID, PAYLOAD);
        verify(fixture.store).lockStatus(EVENT_ID);
        verifyNoInteractions(fixture.provider);
        verify(fixture.store, never()).lockAccount(any());
    }

    @Test
    void doesNotMarkEventProcessedWhenSubaccountCreationFails() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        when(fixture.provider.ensureSubaccount(ACCOUNT_ID)).thenThrow(new IllegalStateException("provider unavailable"));

        assertThatThrownBy(() -> fixture.service.handle(PAYLOAD, "valid"))
                .isInstanceOf(IllegalStateException.class);

        verify(fixture.store, never()).markProcessed(EVENT_ID);
    }

    @Test
    void ignoresAnotherApprovalAfterAccountReachedTerminalStatus() {
        var fixture = fixture(true, "RECEIVED", KycStatus.APPROVED);

        assertThat(fixture.service.handle(PAYLOAD, "valid").idempotentReplay()).isTrue();

        verify(fixture.store).markProcessed(EVENT_ID);
        verifyNoInteractions(fixture.provider);
    }

    private static Fixture fixture(boolean signatureValid, String eventStatus, KycStatus accountStatus) {
        var signatures = mock(WebhookSignatureVerifier.class);
        var store = mock(KycWebhookStore.class);
        var provider = mock(KycProvider.class);
        when(signatures.valid(any(), any())).thenReturn(signatureValid);
        when(store.lockStatus(EVENT_ID)).thenReturn(eventStatus);
        when(store.lockAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new KycWebhookStore.AccountKycState(accountStatus, null)));
        var mapper = new ObjectMapper().findAndRegisterModules();
        return new Fixture(new KycWebhookService(mapper, signatures, store, provider), store, provider);
    }

    private record Fixture(KycWebhookService service, KycWebhookStore store, KycProvider provider) { }
}
