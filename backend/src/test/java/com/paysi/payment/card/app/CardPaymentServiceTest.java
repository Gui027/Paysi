package com.paysi.payment.card.app;

import com.paysi.core.error.ConflictException;
import com.paysi.payment.card.domain.*;
import com.paysi.payment.card.port.CardPaymentRepository;
import com.paysi.payment.provider.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CardPaymentServiceTest {
    private static final UUID CHARGE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void approvedChargePersistsThreeDsEvidence() {
        var fixture = fixture(context(null, null));
        when(fixture.provider.charge(any())).thenReturn(result(ProviderChargeStatus.APPROVED,
                new ProviderThreeDs("AUTHENTICATED", null, "05")));

        var response = fixture.service.start(CHARGE, command());

        assertThat(response.status()).isEqualTo("approved");
        assertThat(response.threeDs().eci()).isEqualTo("05");
        verify(fixture.repository).saveEvidence(CHARGE, command().evidence(),
                new ProviderThreeDs("AUTHENTICATED", null, "05"));
    }

    @Test
    void declinedChargeDoesNotPersistEvidenceOrDependOnLedger() {
        var fixture = fixture(context(null, null));
        when(fixture.provider.charge(any())).thenReturn(result(ProviderChargeStatus.DECLINED,
                new ProviderThreeDs("FAILED", null, null)));

        var response = fixture.service.start(CHARGE, command());

        assertThat(response.status()).isEqualTo("declined");
        assertThat(response.pixAlternativeExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        verify(fixture.repository, never()).saveEvidence(any(), any(), any());
        assertThat(CardPaymentService.class.getDeclaredFields()).noneMatch(field ->
                field.getType().getName().contains("Ledger"));
    }

    @Test
    void pendingChallengeCanBeConfirmedAndInvalidChallengeNeverPersistsEvidence() {
        var pending = context(ProviderChargeStatus.PENDING, "CHALLENGE_REQUIRED");
        var fixture = fixture(pending);
        when(fixture.provider.confirmThreeDs(any())).thenReturn(result(ProviderChargeStatus.DECLINED,
                new ProviderThreeDs("FAILED", null, null)));

        assertThat(fixture.service.confirmThreeDs(CHARGE, "invalid", command().evidence()).status())
                .isEqualTo("declined");
        verify(fixture.repository, never()).saveEvidence(any(), any(), any());

        fixture = fixture(pending);
        when(fixture.provider.confirmThreeDs(any())).thenReturn(result(ProviderChargeStatus.APPROVED,
                new ProviderThreeDs("AUTHENTICATED", null, "05")));
        assertThat(fixture.service.confirmThreeDs(CHARGE, "valid", command().evidence()).status())
                .isEqualTo("approved");
        verify(fixture.repository).saveEvidence(eq(CHARGE), any(), any());
    }

    @Test
    void repeatedStartReturnsStoredResultWithoutChargingAgain() {
        var fixture = fixture(context(ProviderChargeStatus.APPROVED, "AUTHENTICATED"));
        var response = fixture.service.start(CHARGE, command());
        assertThat(response.idempotentReplay()).isTrue();
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void confirmationOutsidePendingChallengeFails() {
        var fixture = fixture(context(ProviderChargeStatus.DECLINED, "FAILED"));
        assertThatThrownBy(() -> fixture.service.confirmThreeDs(CHARGE, "token", command().evidence()))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void pixAlternativeIsEnforcedForExactlyTheStoredWindow() {
        var fixture = fixture(context(ProviderChargeStatus.DECLINED, "FAILED"));
        assertThat(fixture.service.requireAvailablePixAlternative(CHARGE))
                .isEqualTo(NOW.plus(Duration.ofHours(24)));

        var expired = context(ProviderChargeStatus.DECLINED, "FAILED");
        expired = new CardPaymentRepository.CardChargeContext(expired.chargeId(), expired.orderId(),
                expired.amountCents(), expired.installments(), expired.buyer(), expired.split(),
                expired.providerChargeId(), expired.providerStatus(), expired.threeDsStatus(),
                expired.challengeUrl(), expired.eci(), NOW);
        var expiredFixture = fixture(expired);
        assertThatThrownBy(() -> expiredFixture.service.requireAvailablePixAlternative(CHARGE))
                .isInstanceOf(ConflictException.class);
    }

    private static Fixture fixture(CardPaymentRepository.CardChargeContext context) {
        var repository = mock(CardPaymentRepository.class);
        var provider = mock(PaymentProvider.class);
        when(repository.lockCharge(CHARGE)).thenReturn(Optional.of(context));
        return new Fixture(new CardPaymentService(repository, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)), repository, provider);
    }

    private static CardPaymentRepository.CardChargeContext context(ProviderChargeStatus status,
                                                                    String threeDsStatus) {
        return new CardPaymentRepository.CardChargeContext(CHARGE, ORDER, 10_000, 2,
                new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725"),
                new ProviderSplit(8_000, 500, 1_500),
                status == null ? null : "provider-charge", status, threeDsStatus,
                threeDsStatus != null && threeDsStatus.equals("CHALLENGE_REQUIRED")
                        ? "https://challenge" : null,
                status == ProviderChargeStatus.APPROVED ? "05" : null,
                status == ProviderChargeStatus.APPROVED ? null : NOW.plus(Duration.ofHours(24)));
    }

    private static ProviderPaymentResult result(ProviderChargeStatus status, ProviderThreeDs threeDs) {
        return new ProviderPaymentResult("provider-charge", status, null, 200, List.of(), threeDs,
                status == ProviderChargeStatus.ERROR ? "PROVIDER_ERROR" : null, false);
    }

    private static CardPaymentCommand command() {
        return new CardPaymentCommand("tok_test", 2,
                new SaleEvidenceCommand("127.0.0.1", "agent", "device", "terms-v1", NOW));
    }

    private record Fixture(CardPaymentService service, CardPaymentRepository repository,
                           PaymentProvider provider) {}
}
