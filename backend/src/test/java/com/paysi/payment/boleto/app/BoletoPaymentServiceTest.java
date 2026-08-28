package com.paysi.payment.boleto.app;

import com.paysi.core.error.ValidationException;
import com.paysi.payment.boleto.port.BoletoRepository;
import com.paysi.payment.provider.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BoletoPaymentServiceTest {
    private static final UUID CHARGE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Test
    void issuesBoletoWithValidatedDueDateAndPersistsProviderData() {
        var fixture = fixture(context(null));
        var providerResult = result(NOW.plus(Duration.ofDays(15)));
        when(fixture.provider.charge(any())).thenReturn(providerResult);

        var issued = fixture.service.issue(CHARGE, 15);

        assertThat(issued.barcode()).isEqualTo("34191-test");
        assertThat(issued.dueAt()).isEqualTo(NOW.plus(Duration.ofDays(15)));
        var request = org.mockito.ArgumentCaptor.forClass(ProviderPaymentRequest.class);
        verify(fixture.provider).charge(request.capture());
        assertThat(request.getValue().method()).isEqualTo(ProviderPaymentMethod.BOLETO);
        assertThat(request.getValue().boletoDueDays()).isEqualTo(15);
        verify(fixture.repository).saveIssued(CHARGE, providerResult);
    }

    @Test
    void rejectsDueDateOutsideOneToFifteenDays() {
        var fixture = fixture(context(null));
        assertThatThrownBy(() -> fixture.service.issue(CHARGE, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> fixture.service.issue(CHARGE, 16)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void replaysExistingBoletoAndExpirationJobRecordsExpiredRows() {
        var fixture = fixture(context(ProviderChargeStatus.PENDING));
        assertThat(fixture.service.issue(CHARGE, 3).idempotentReplay()).isTrue();
        verifyNoInteractions(fixture.provider);

        when(fixture.repository.expireDue(NOW)).thenReturn(2);
        assertThat(fixture.service.expireDue()).isEqualTo(2);
    }

    private static Fixture fixture(BoletoRepository.BoletoChargeContext context) {
        var repository = mock(BoletoRepository.class);
        var provider = mock(PaymentProvider.class);
        when(repository.lockCharge(CHARGE)).thenReturn(Optional.of(context));
        return new Fixture(new BoletoPaymentService(repository, provider,
                Clock.fixed(NOW, ZoneOffset.UTC)), repository, provider);
    }

    private static BoletoRepository.BoletoChargeContext context(ProviderChargeStatus status) {
        return new BoletoRepository.BoletoChargeContext(CHARGE, ORDER, 10_000,
                new ProviderBuyer("Buyer", "buyer@example.com", "PF", "52998224725"),
                new ProviderSplit(8_000, 500, 1_500), status == null ? null : "provider-charge", status,
                status == null ? null : "34191-test", status == null ? null : "https://boleto",
                status == null ? null : NOW.plus(Duration.ofDays(3)));
    }

    private static ProviderPaymentResult result(Instant dueAt) {
        return new ProviderPaymentResult("provider-charge", ProviderChargeStatus.PENDING,
                new ProviderPaymentData(null, "34191-test", "https://boleto", dueAt), 200,
                List.of(), new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(BoletoPaymentService service, BoletoRepository repository,
                           PaymentProvider provider) {}
}
