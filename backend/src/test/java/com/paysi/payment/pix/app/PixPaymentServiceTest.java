package com.paysi.payment.pix.app;

import com.paysi.payment.pix.port.PixRepository;
import com.paysi.payment.provider.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class PixPaymentServiceTest {
    private static final UUID CHARGE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void issuesPixQrCodeAndPersistsProviderData() {
        var fixture = fixture(context(null));
        var providerResult = result(NOW.plus(Duration.ofMinutes(30)));
        when(fixture.provider.charge(any())).thenReturn(providerResult);

        var issued = fixture.service.start(CHARGE);

        assertThat(issued.qrCode()).isEqualTo("000201-test");
        assertThat(issued.status()).isEqualTo("PENDING");
        assertThat(issued.idempotentReplay()).isFalse();
        verify(fixture.repository).saveIssued(CHARGE, providerResult);
        verify(fixture.provider).charge(argThat(request -> request.method() == ProviderPaymentMethod.PIX
                && request.orderId().equals(ORDER)));
    }

    @Test
    void replaysExistingPixWithoutCallingProviderAgain() {
        var fixture = fixture(context(ProviderChargeStatus.PENDING));

        var replayed = fixture.service.start(CHARGE);

        assertThat(replayed.idempotentReplay()).isTrue();
        assertThat(replayed.qrCode()).isEqualTo("000201-test");
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void failsFastWhenProviderOmitsPixData() {
        var fixture = fixture(context(null));
        when(fixture.provider.charge(any())).thenReturn(new ProviderPaymentResult("prov-1",
                ProviderChargeStatus.PENDING, null, 0, List.of(),
                new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false));

        assertThatThrownBy(() -> fixture.service.start(CHARGE)).isInstanceOf(IllegalStateException.class);
    }

    private static Fixture fixture(PixRepository.PixChargeContext context) {
        var repository = mock(PixRepository.class);
        var provider = mock(PaymentProvider.class);
        when(repository.lockCharge(CHARGE)).thenReturn(Optional.of(context));
        return new Fixture(new PixPaymentService(repository, provider), repository, provider);
    }

    private static PixRepository.PixChargeContext context(ProviderChargeStatus status) {
        return new PixRepository.PixChargeContext(CHARGE, ORDER, 10_000,
                new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725"),
                new ProviderSplit(8_000, 500, 1_500), status == null ? null : "provider-charge", status,
                status == null ? null : "000201-test", status == null ? null : NOW.plus(Duration.ofMinutes(30)));
    }

    private static ProviderPaymentResult result(Instant expiresAt) {
        return new ProviderPaymentResult("provider-charge", ProviderChargeStatus.PENDING,
                new ProviderPaymentData("000201-test", null, null, expiresAt), 0,
                List.of(), new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(PixPaymentService service, PixRepository repository, PaymentProvider provider) {
    }
}
