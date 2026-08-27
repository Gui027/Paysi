package com.paysi.identity.kyc.webhook.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.port.KycProvider;
import com.paysi.identity.kyc.webhook.port.KycWebhookStore;
import com.paysi.identity.kyc.webhook.port.WebhookSignatureVerifier;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.Bucket;
import com.paysi.ledger.domain.Direction;
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

        verifyNoInteractions(fixture.store, fixture.provider, fixture.ledger);
    }

    @Test
    void appliesRejectionWithoutCreatingSubaccountOrChargingFee() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        String payload = PAYLOAD.replace("APPROVED", "REJECTED");

        var result = fixture.service.handle(payload, "valid");

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.store).apply(ACCOUNT_ID, KycStatus.REJECTED, null, java.util.List.of());
        verify(fixture.store).markProcessed(EVENT_ID);
        verifyNoInteractions(fixture.provider, fixture.ledger);
    }

    @Test
    void approvesAccountCreatesSubaccountAndChargesBalancedFee() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        when(fixture.provider.ensureSubaccount(ACCOUNT_ID)).thenReturn("subaccount-one");

        var result = fixture.service.handle(PAYLOAD, "valid");

        assertThat(result.idempotentReplay()).isFalse();
        verify(fixture.store).apply(ACCOUNT_ID, KycStatus.APPROVED, "subaccount-one", java.util.List.of());
        var command = org.mockito.ArgumentCaptor.forClass(com.paysi.ledger.domain.LedgerCommand.class);
        verify(fixture.ledger).write(command.capture());
        assertThat(command.getValue().entries()).hasSize(2);
        assertThat(command.getValue().entries().get(0).bucket()).isEqualTo(Bucket.DEBT);
        assertThat(command.getValue().entries().get(0).direction()).isEqualTo(Direction.DEBIT);
        assertThat(command.getValue().entries().get(0).amountCents()).isEqualTo(1200);
        assertThat(command.getValue().entries().get(1).direction()).isEqualTo(Direction.CREDIT);
        verify(fixture.store).markProcessed(EVENT_ID);
    }

    @Test
    void treatsProcessedEventAsIdempotentReplay() {
        var fixture = fixture(true, "PROCESSED", KycStatus.SUBMITTED);

        assertThat(fixture.service.handle(PAYLOAD, "valid").idempotentReplay()).isTrue();

        verify(fixture.store).receive(EVENT_ID, PAYLOAD);
        verify(fixture.store).lockStatus(EVENT_ID);
        verifyNoInteractions(fixture.provider, fixture.ledger);
        verify(fixture.store, never()).lockAccount(any());
    }

    @Test
    void doesNotMarkEventProcessedWhenLedgerWriteFails() {
        var fixture = fixture(true, "RECEIVED", KycStatus.SUBMITTED);
        when(fixture.provider.ensureSubaccount(ACCOUNT_ID)).thenReturn("subaccount-one");
        doThrow(new IllegalStateException("ledger unavailable")).when(fixture.ledger).write(any());

        assertThatThrownBy(() -> fixture.service.handle(PAYLOAD, "valid"))
                .isInstanceOf(IllegalStateException.class);

        verify(fixture.store, never()).markProcessed(EVENT_ID);
    }

    @Test
    void ignoresAnotherApprovalAfterAccountReachedTerminalStatus() {
        var fixture = fixture(true, "RECEIVED", KycStatus.APPROVED);

        assertThat(fixture.service.handle(PAYLOAD, "valid").idempotentReplay()).isTrue();

        verify(fixture.store).markProcessed(EVENT_ID);
        verifyNoInteractions(fixture.provider, fixture.ledger);
    }

    private static Fixture fixture(boolean signatureValid, String eventStatus, KycStatus accountStatus) {
        var signatures = mock(WebhookSignatureVerifier.class);
        var store = mock(KycWebhookStore.class);
        var provider = mock(KycProvider.class);
        var ledger = mock(LedgerService.class);
        when(signatures.valid(any(), any())).thenReturn(signatureValid);
        when(store.lockStatus(EVENT_ID)).thenReturn(eventStatus);
        when(store.lockAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new KycWebhookStore.AccountKycState(accountStatus, null)));
        var mapper = new ObjectMapper().findAndRegisterModules();
        return new Fixture(new KycWebhookService(mapper, signatures, store, provider, ledger), store, provider, ledger);
    }

    private record Fixture(KycWebhookService service, KycWebhookStore store, KycProvider provider,
                           LedgerService ledger) { }
}
