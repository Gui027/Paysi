package com.paysi.payment.receivable.app;

import com.paysi.core.error.ConflictException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.receivable.domain.ProviderInstallment;
import com.paysi.payment.receivable.domain.Receivable;
import com.paysi.payment.receivable.domain.ReceivableSchedule.ChargeReceivableTerms;
import com.paysi.payment.receivable.port.ReceivableRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ReceivableScheduleServiceTest {
    private static final UUID CHARGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant EXPECTED_AT = Instant.parse("2026-09-01T12:00:00Z");
    private static final List<ProviderInstallment> PROVIDER_SCHEDULE = List.of(
            new ProviderInstallment(1, "provider-1", EXPECTED_AT, 501),
            new ProviderInstallment(2, "provider-2", EXPECTED_AT.plusSeconds(86_400), 500));

    @Test
    void persistsScheduleOnlyOnce() {
        var repository = repository();
        var service = new ReceivableScheduleService(repository);

        var result = service.create(CHARGE_ID, PROVIDER_SCHEDULE);

        assertThat(result.receivables()).isEqualTo(2);
        assertThat(result.idempotentReplay()).isFalse();
        verify(repository).insert(anyList());
    }

    @Test
    void repeatedEquivalentScheduleIsAnIdempotentReplay() {
        var repository = repository();
        var service = new ReceivableScheduleService(repository);
        var existing = List.of(
                receivable(1, "provider-1", EXPECTED_AT, 501, 401, 50),
                receivable(2, "provider-2", EXPECTED_AT.plusSeconds(86_400), 500, 400, 50));
        when(repository.findByCharge(CHARGE_ID)).thenReturn(existing);

        var result = service.create(CHARGE_ID, PROVIDER_SCHEDULE);

        assertThat(result.idempotentReplay()).isTrue();
        verify(repository, never()).insert(anyList());
    }

    @Test
    void refusesToRecalculateFrozenSchedule() {
        var repository = repository();
        var service = new ReceivableScheduleService(repository);
        when(repository.findByCharge(CHARGE_ID)).thenReturn(List.of(
                receivable(1, "different", EXPECTED_AT, 501, 401, 50),
                receivable(2, "provider-2", EXPECTED_AT.plusSeconds(86_400), 500, 400, 50)));

        assertThatThrownBy(() -> service.create(CHARGE_ID, PROVIDER_SCHEDULE))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).insert(anyList());
    }

    @Test
    void reportsProviderDivergenceWithoutWriting() {
        var repository = repository();
        var service = new ReceivableScheduleService(repository);
        var invalid = List.of(new ProviderInstallment(1, "provider-1", EXPECTED_AT, 1_000));

        assertThatThrownBy(() -> service.create(CHARGE_ID, invalid))
                .isInstanceOf(ValidationException.class);
        verify(repository, never()).insert(anyList());
    }

    private static ReceivableRepository repository() {
        var repository = mock(ReceivableRepository.class);
        when(repository.lockCharge(CHARGE_ID))
                .thenReturn(Optional.of(new ChargeReceivableTerms(CHARGE_ID, 2, 1_001, 801, 100)));
        when(repository.findByCharge(CHARGE_ID)).thenReturn(List.of());
        return repository;
    }

    private static Receivable receivable(int sequence, String providerId, Instant expectedAt, long amount,
                                         long seller, long affiliate) {
        return new Receivable(UUID.randomUUID(), CHARGE_ID, sequence, providerId, expectedAt,
                amount, seller, affiliate);
    }
}
