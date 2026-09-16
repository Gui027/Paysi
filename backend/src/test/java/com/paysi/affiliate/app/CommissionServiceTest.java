package com.paysi.affiliate.app;

import com.paysi.affiliate.port.AffiliateAttributionRepository;
import com.paysi.affiliate.port.AffiliateAttributionRepository.Attribution;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommissionServiceTest {
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final UUID AFFILIATION = UUID.randomUUID();
    private static final UUID CHARGE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void registerClickRequiresApprovedAffiliation() {
        var fixture = fixture();
        when(fixture.attribution.findApprovedAffiliation(PRODUCT, AFFILIATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.service.registerClick(PRODUCT, AFFILIATE, "visitor-1", "1.2.3.4"))
                .isInstanceOf(NotFoundException.class);
        verify(fixture.attribution, never()).recordClick(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registerClickStoresClickWithSixtyDayWindow() {
        var fixture = fixture();
        when(fixture.attribution.findApprovedAffiliation(PRODUCT, AFFILIATE)).thenReturn(Optional.of(AFFILIATION));

        UUID result = fixture.service.registerClick(PRODUCT, AFFILIATE, "visitor-1", "1.2.3.4");

        assertThat(result).isEqualTo(AFFILIATION);
        verify(fixture.attribution).recordClick(any(), eq(AFFILIATION), eq(PRODUCT), eq("visitor-1"),
                eq("1.2.3.4"), eq(NOW), eq(NOW.plus(Duration.ofDays(60))));
    }

    @Test
    void registerClickRejectsBlankVisitorKey() {
        var fixture = fixture();
        assertThatThrownBy(() -> fixture.service.registerClick(PRODUCT, AFFILIATE, "  ", "1.2.3.4"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveForChargeIgnoresRenewalsWhenNotAllCycles() {
        var fixture = fixture();
        when(fixture.attribution.resolveAttribution(PRODUCT, "visitor-1", NOW))
                .thenReturn(Optional.of(new Attribution(AFFILIATION, AFFILIATE, 1000, false)));

        assertThat(fixture.service.resolveForCharge(PRODUCT, "visitor-1", 1)).isPresent();
        assertThat(fixture.service.resolveForCharge(PRODUCT, "visitor-1", 2)).isEmpty();
    }

    @Test
    void resolveForChargeKeepsRenewalsWhenAllCycles() {
        var fixture = fixture();
        when(fixture.attribution.resolveAttribution(PRODUCT, "visitor-1", NOW))
                .thenReturn(Optional.of(new Attribution(AFFILIATION, AFFILIATE, 1000, true)));

        assertThat(fixture.service.resolveForCharge(PRODUCT, "visitor-1", 5)).isPresent();
    }

    @Test
    void liquidateWritesBalancedGuaranteeCreditForAffiliate() {
        var fixture = fixture();

        fixture.service.liquidate(AFFILIATE, 1_500, CHARGE, NOW, 7);

        ArgumentCaptor<LedgerCommand> captor = ArgumentCaptor.forClass(LedgerCommand.class);
        verify(fixture.ledger).write(captor.capture());
        var command = captor.getValue();
        assertThat(command.type()).isEqualTo(TransactionType.COMMISSION);
        assertThat(command.reference()).isEqualTo(new LedgerReference(ReferenceType.CHARGE, CHARGE + ":commission"));
        assertThat(command.entries()).hasSize(2);
        var credit = command.entries().stream().filter(e -> e.direction() == Direction.CREDIT).findFirst().orElseThrow();
        assertThat(credit.accountId()).isEqualTo(AFFILIATE);
        assertThat(credit.bucket()).isEqualTo(Bucket.GUARANTEE);
        assertThat(credit.amountCents()).isEqualTo(1_500);
        assertThat(credit.origin()).isEqualTo(Origin.COMMISSION);
        assertThat(credit.releaseAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        var debit = command.entries().stream().filter(e -> e.direction() == Direction.DEBIT).findFirst().orElseThrow();
        assertThat(debit.bucket()).isEqualTo(Bucket.SYSTEM);
        assertThat(debit.amountCents()).isEqualTo(1_500);
    }

    @Test
    void liquidateSkipsZeroOrNegativeAmounts() {
        var fixture = fixture();
        fixture.service.liquidate(AFFILIATE, 0, CHARGE, NOW, 7);
        verifyNoInteractions(fixture.ledger);
    }

    private static Fixture fixture() {
        var attribution = mock(AffiliateAttributionRepository.class);
        var ledger = mock(LedgerService.class);
        var service = new CommissionService(attribution, ledger, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, attribution, ledger);
    }

    private record Fixture(CommissionService service, AffiliateAttributionRepository attribution, LedgerService ledger) {
    }
}
