package com.paysi.payment.receivable.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceivableScheduleTest {
    private static final UUID CHARGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant EXPECTED_AT = Instant.parse("2026-09-01T12:00:00Z");

    @Test
    void allocatesTwelveInstallmentsWithExactTotalsAndFirstCentRemainders() {
        var terms = new ReceivableSchedule.ChargeReceivableTerms(CHARGE_ID, 12, 12_005, 10_003, 1_001);
        var schedule = ReceivableSchedule.create(terms, providerSchedule(12_005, 12));

        assertThat(schedule).hasSize(12);
        assertThat(schedule).extracting(Receivable::amountCents).containsExactly(
                1001L, 1001L, 1001L, 1001L, 1001L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L);
        assertThat(schedule.stream().mapToLong(Receivable::amountCents).sum()).isEqualTo(12_005);
        assertThat(schedule.stream().mapToLong(Receivable::sellerAmountCents).sum()).isEqualTo(10_003);
        assertThat(schedule.stream().mapToLong(Receivable::affiliateAmountCents).sum()).isEqualTo(1_001);
        assertThat(schedule.get(0).sellerAmountCents()).isEqualTo(834);
        assertThat(schedule.get(11).sellerAmountCents()).isEqualTo(833);
    }

    @Test
    void rejectsProviderScheduleWithWrongTotal() {
        var terms = new ReceivableSchedule.ChargeReceivableTerms(CHARGE_ID, 2, 2_000, 1_500, 100);

        assertThatThrownBy(() -> ReceivableSchedule.create(terms, List.of(
                installment(1, 1_000), installment(2, 999))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total");
    }

    @Test
    void rejectsDuplicateSequenceOrProviderId() {
        var terms = new ReceivableSchedule.ChargeReceivableTerms(CHARGE_ID, 2, 2_000, 1_500, 100);

        assertThatThrownBy(() -> ReceivableSchedule.create(terms, List.of(
                installment(1, 1_000), new ProviderInstallment(1, "provider-1", EXPECTED_AT, 1_000))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<ProviderInstallment> providerSchedule(long total, int installments) {
        long base = total / installments;
        long remainder = total % installments;
        return IntStream.range(0, installments)
                .mapToObj(index -> installment(index + 1, base + (index < remainder ? 1 : 0)))
                .toList();
    }

    private static ProviderInstallment installment(int sequence, long amount) {
        return new ProviderInstallment(sequence, "provider-" + sequence, EXPECTED_AT.plusSeconds(sequence), amount);
    }
}
