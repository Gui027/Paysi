package com.paysi.billing.plan.app;

import com.paysi.billing.plan.domain.PlanStatus;
import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.port.PlanRepository;
import com.paysi.core.error.ValidationException;
import com.paysi.ledger.app.LedgerService;
import com.paysi.ledger.domain.LedgerCommand;
import com.paysi.payment.provider.*;
import com.paysi.payment.split.Plan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanBillingProcessorTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void transacionalRolloverJustAdvancesPeriodWithoutCharging() {
        var fixture = fixture();
        when(fixture.repository.claimDueRollover(NOW)).thenReturn(Optional.of(subscription(Plan.TRANSACIONAL, null)));

        boolean processed = fixture.processor.processNextRollover();

        assertThat(processed).isTrue();
        verify(fixture.repository).applyRollover(eq(ACCOUNT), eq("TRANSACIONAL"), eq(0L), eq(PERIOD_END), any(),
                eq("ACTIVE"), isNull());
        verifyNoInteractions(fixture.ledger, fixture.provider);
    }

    @Test
    void escalaRolloverDebitsAvailableBalanceWhenSufficient() {
        var fixture = fixture();
        when(fixture.repository.claimDueRollover(NOW)).thenReturn(Optional.of(subscription(Plan.ESCALA, "tok_1")));
        when(fixture.ledger.write(any())).thenReturn(null);

        fixture.processor.processNextRollover();

        verify(fixture.ledger).write(any(LedgerCommand.class));
        verifyNoInteractions(fixture.provider);
        verify(fixture.repository).applyRollover(eq(ACCOUNT), eq("ESCALA"), eq(19_900L), eq(PERIOD_END), any(),
                eq("ACTIVE"), isNull());
    }

    @Test
    void escalaRolloverFallsBackToCardWhenBalanceInsufficient() {
        var fixture = fixture();
        when(fixture.repository.claimDueRollover(NOW)).thenReturn(Optional.of(subscription(Plan.ESCALA, "tok_1")));
        when(fixture.ledger.write(any(LedgerCommand.class)))
                .thenThrow(new ValidationException("LEDGER_INSUFFICIENT_BALANCE", "sem saldo", null))
                .thenReturn(null);
        when(fixture.repository.billingInfo(ACCOUNT))
                .thenReturn(new PlanRepository.AccountBillingInfo("Vendedor", "v@example.com", "PF", "52998224725"));
        when(fixture.provider.charge(any())).thenReturn(approved());

        fixture.processor.processNextRollover();

        verify(fixture.provider).charge(any());
        verify(fixture.repository).applyRollover(eq(ACCOUNT), eq("ESCALA"), eq(19_900L), eq(PERIOD_END), any(),
                eq("ACTIVE"), isNull());
    }

    @Test
    void escalaRolloverMarksPastDueWhenNoCardAndInsufficientBalance() {
        var fixture = fixture();
        when(fixture.repository.claimDueRollover(NOW)).thenReturn(Optional.of(subscription(Plan.ESCALA, null)));
        when(fixture.ledger.write(any(LedgerCommand.class)))
                .thenThrow(new ValidationException("LEDGER_INSUFFICIENT_BALANCE", "sem saldo", null));

        fixture.processor.processNextRollover();

        verifyNoInteractions(fixture.provider);
        verify(fixture.repository).applyRollover(eq(ACCOUNT), eq("ESCALA"), eq(19_900L), eq(PERIOD_END), any(),
                eq("PAST_DUE"), eq(NOW));
    }

    @Test
    void downgradeAfterTenDaysPastDueResetsToTransacional() {
        var fixture = fixture();
        when(fixture.repository.claimDueDowngrade(NOW)).thenReturn(Optional.of(ACCOUNT));

        boolean processed = fixture.processor.processNextDowngrade();

        assertThat(processed).isTrue();
        verify(fixture.repository).applyDowngrade(eq(ACCOUNT), any(), any());
    }

    private static Fixture fixture() {
        var repository = mock(PlanRepository.class);
        var ledger = mock(LedgerService.class);
        var provider = mock(PaymentProvider.class);
        var processor = new PlanBillingProcessor(repository, ledger, provider, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(processor, repository, ledger, provider);
    }

    private static PlatformSubscription subscription(Plan plan, String providerToken) {
        long price = plan == Plan.ESCALA ? 19_900 : 0;
        return new PlatformSubscription(ACCOUNT, plan, price, PERIOD_END.minusSeconds(30L * 86_400), PERIOD_END,
                PlanStatus.ACTIVE, null, null, null, null, providerToken);
    }

    private static ProviderPaymentResult approved() {
        return new ProviderPaymentResult("prov-1", ProviderChargeStatus.APPROVED, null, 0, List.of(),
                new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(PlanBillingProcessor processor, PlanRepository repository, LedgerService ledger,
                           PaymentProvider provider) {
    }
}
