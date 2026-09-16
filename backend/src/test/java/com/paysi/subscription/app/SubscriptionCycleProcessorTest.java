package com.paysi.subscription.app;

import com.paysi.affiliate.app.CommissionService;
import com.paysi.identity.port.PlatformPlanReader;
import com.paysi.payment.provider.*;
import com.paysi.subscription.port.SubscriptionRepository;
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

class SubscriptionCycleProcessorTest {
    private static final UUID SUBSCRIPTION = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void trialEndWithCardChargesFirstCycleAndActivates() {
        var fixture = fixture();
        when(fixture.repository.claimDueCancellation(NOW)).thenReturn(Optional.empty());
        when(fixture.repository.claimDueCycle(NOW)).thenReturn(Optional.of(cycle("CARD", 1, "tok_1")));
        when(fixture.provider.charge(any())).thenReturn(cardResult(ProviderChargeStatus.APPROVED));

        boolean processed = fixture.processor.processNextCycle();

        assertThat(processed).isTrue();
        verify(fixture.repository).updateSubscriptionCycle(eq(SUBSCRIPTION), eq("ACTIVE"), any());
        verify(fixture.repository).markOrderStatus(any(), eq("PAID"), any());
    }

    @Test
    void trialEndWithoutCardStaysPastDueWithoutCallingProvider() {
        var fixture = fixture();
        when(fixture.repository.claimDueCancellation(NOW)).thenReturn(Optional.empty());
        when(fixture.repository.claimDueCycle(NOW)).thenReturn(Optional.of(cycle("CARD", 1, null)));

        fixture.processor.processNextCycle();

        verify(fixture.repository).updateSubscriptionStatus(SUBSCRIPTION, "PAST_DUE");
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void boletoRenewalIssuesPendingChargeAndAdvancesCycleOptimistically() {
        var fixture = fixture();
        when(fixture.repository.claimDueCancellation(NOW)).thenReturn(Optional.empty());
        when(fixture.repository.claimDueCycle(NOW)).thenReturn(Optional.of(cycle("BOLETO", 3, null)));
        when(fixture.provider.charge(any())).thenReturn(boletoResult());

        fixture.processor.processNextCycle();

        verify(fixture.repository).saveChargeResult(any(), eq("PENDING"), any(), anyLong(), isNull(), isNull(), isNull());
        verify(fixture.repository).updateSubscriptionCycle(eq(SUBSCRIPTION), eq("ACTIVE"), any());
    }

    @Test
    void dueCancellationTakesPriorityOverCycleAndAppliesWithoutCharging() {
        var fixture = fixture();
        when(fixture.repository.claimDueCancellation(NOW)).thenReturn(Optional.of(SUBSCRIPTION));

        boolean processed = fixture.processor.processNextCancellation();

        assertThat(processed).isTrue();
        verify(fixture.repository).applyCancellation(SUBSCRIPTION);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void declinedCardRenewalSchedulesFirstRetry() {
        var fixture = fixture();
        when(fixture.repository.claimDueCancellation(NOW)).thenReturn(Optional.empty());
        when(fixture.repository.claimDueCycle(NOW)).thenReturn(Optional.of(cycle("CARD", 3, "tok_1")));
        when(fixture.provider.charge(any())).thenReturn(cardResult(ProviderChargeStatus.DECLINED));

        fixture.processor.processNextCycle();

        verify(fixture.repository).saveChargeResult(any(), eq("FAILED"), any(), anyLong(), isNull(), isNull(),
                eq(NOW.plus(java.time.Duration.ofDays(1))));
        verify(fixture.repository).updateSubscriptionCycle(SUBSCRIPTION, "PAST_DUE", null);
    }

    private static Fixture fixture() {
        var repository = mock(SubscriptionRepository.class);
        var plans = mock(PlatformPlanReader.class);
        var provider = mock(PaymentProvider.class);
        var commissions = mock(CommissionService.class);
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        var processor = new SubscriptionCycleProcessor(repository, plans, provider, commissions,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(processor, repository, provider);
    }

    private static SubscriptionRepository.DueCycle cycle(String orderMethod, int nextCycleNumber, String token) {
        return new SubscriptionRepository.DueCycle(SUBSCRIPTION, UUID.randomUUID(), UUID.randomUUID(), SELLER,
                10_000, "MONTHLY", orderMethod, 3, 7, "Comprador", "buyer@example.com", "PF", "52998224725",
                token, nextCycleNumber, nextCycleNumber == 1, null, 0, false);
    }

    private static ProviderPaymentResult cardResult(ProviderChargeStatus status) {
        return new ProviderPaymentResult("prov-1", status, null, 199, List.of(),
                new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private static ProviderPaymentResult boletoResult() {
        return new ProviderPaymentResult("prov-boleto-1", ProviderChargeStatus.PENDING, null, 0, List.of(),
                new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(SubscriptionCycleProcessor processor, SubscriptionRepository repository,
                           PaymentProvider provider) {
    }
}
