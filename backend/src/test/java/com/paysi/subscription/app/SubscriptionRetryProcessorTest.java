package com.paysi.subscription.app;

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

class SubscriptionRetryProcessorTest {
    private static final UUID CHARGE = UUID.randomUUID();
    private static final UUID SUBSCRIPTION = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    @Test
    void approvedRetryReactivatesSubscriptionAndSchedulesNextCycle() {
        var fixture = fixture();
        when(fixture.repository.claimDueRetry(NOW)).thenReturn(Optional.of(retry(2)));
        when(fixture.provider.charge(any())).thenReturn(result(ProviderChargeStatus.APPROVED));

        boolean processed = fixture.processor.processNext();

        assertThat(processed).isTrue();
        verify(fixture.repository).saveChargeResult(eq(CHARGE), eq("PAID"), any(), anyLong(), eq(NOW), eq(NOW), isNull());
        verify(fixture.repository).updateSubscriptionCycle(eq(SUBSCRIPTION), eq("ACTIVE"),
                eq(NOW.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()));
    }

    @Test
    void declinedRetryReschedulesAccordingToDunningSchedule() {
        var fixture = fixture();
        when(fixture.repository.claimDueRetry(NOW)).thenReturn(Optional.of(retry(1)));
        when(fixture.provider.charge(any())).thenReturn(result(ProviderChargeStatus.DECLINED));

        fixture.processor.processNext();

        verify(fixture.repository).scheduleRetry(CHARGE, 2, NOW.plus(java.time.Duration.ofDays(2)));
        verify(fixture.repository, never()).exhaustRetry(any(), any());
    }

    @Test
    void exhaustedRetriesCancelSubscription() {
        var fixture = fixture();
        when(fixture.repository.claimDueRetry(NOW)).thenReturn(Optional.of(retry(4)));
        when(fixture.provider.charge(any())).thenReturn(result(ProviderChargeStatus.DECLINED));

        fixture.processor.processNext();

        verify(fixture.repository).exhaustRetry(CHARGE, SUBSCRIPTION);
        verify(fixture.repository, never()).scheduleRetry(any(), anyInt(), any());
    }

    @Test
    void missingProviderTokenGivesUpWithoutCallingProvider() {
        var fixture = fixture();
        when(fixture.repository.claimDueRetry(NOW)).thenReturn(Optional.of(retryWithoutToken()));

        fixture.processor.processNext();

        verify(fixture.repository).exhaustRetry(CHARGE, SUBSCRIPTION);
        verifyNoInteractions(fixture.provider);
    }

    @Test
    void nothingDueReturnsFalse() {
        var fixture = fixture();
        when(fixture.repository.claimDueRetry(NOW)).thenReturn(Optional.empty());

        assertThat(fixture.processor.processNext()).isFalse();
        verifyNoInteractions(fixture.provider);
    }

    private static Fixture fixture() {
        var repository = mock(SubscriptionRepository.class);
        var plans = mock(PlatformPlanReader.class);
        var provider = mock(PaymentProvider.class);
        when(plans.currentPlan(SELLER)).thenReturn("TRANSACIONAL");
        var processor = new SubscriptionRetryProcessor(repository, plans, provider,
                Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(processor, repository, provider);
    }

    private static SubscriptionRepository.DueRetry retry(int attemptCount) {
        return new SubscriptionRepository.DueRetry(CHARGE, SUBSCRIPTION, UUID.randomUUID(), UUID.randomUUID(),
                SELLER, 10_000, 2, attemptCount, "MONTHLY", "Comprador", "buyer@example.com", "PF",
                "52998224725", "tok_1");
    }

    private static SubscriptionRepository.DueRetry retryWithoutToken() {
        return new SubscriptionRepository.DueRetry(CHARGE, SUBSCRIPTION, UUID.randomUUID(), UUID.randomUUID(),
                SELLER, 10_000, 2, 1, "MONTHLY", "Comprador", "buyer@example.com", "PF", "52998224725", null);
    }

    private static ProviderPaymentResult result(ProviderChargeStatus status) {
        return new ProviderPaymentResult("prov-1", status, null, 199, List.of(),
                new ProviderThreeDs("NOT_APPLICABLE", null, null), null, false);
    }

    private record Fixture(SubscriptionRetryProcessor processor, SubscriptionRepository repository,
                           PaymentProvider provider) {
    }
}
