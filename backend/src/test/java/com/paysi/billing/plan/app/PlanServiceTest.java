package com.paysi.billing.plan.app;

import com.paysi.billing.plan.domain.PlanStatus;
import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.port.PlanRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.split.Plan;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanServiceTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void changingToEscalaRequiresCardWhenNoneOnFile() {
        var fixture = fixture(subscription(Plan.TRANSACIONAL, null));

        assertThatThrownBy(() -> fixture.service.requestChange(ACCOUNT, Plan.ESCALA, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void changingToEscalaWithCardSchedulesPendingChangeAtPeriodEnd() {
        var fixture = fixture(subscription(Plan.TRANSACIONAL, null));

        fixture.service.requestChange(ACCOUNT, Plan.ESCALA, "tok_1");

        verify(fixture.repository).schedulePendingChange(ACCOUNT, Plan.ESCALA, 19_900, PERIOD_END, "tok_1");
        verify(fixture.repository).insertHistory(any(), eq(ACCOUNT), eq("TRANSACIONAL"), eq("ESCALA"), eq(ACCOUNT),
                any(), eq(NOW));
    }

    @Test
    void changingToEscalaReusesExistingCardWhenNoneProvided() {
        var fixture = fixture(subscription(Plan.TRANSACIONAL, "tok_existing"));

        fixture.service.requestChange(ACCOUNT, Plan.ESCALA, null);

        verify(fixture.repository).schedulePendingChange(ACCOUNT, Plan.ESCALA, 19_900, PERIOD_END, null);
    }

    @Test
    void downgradingToTransacionalNeverRequiresCard() {
        var fixture = fixture(subscription(Plan.ESCALA, "tok_1"));

        fixture.service.requestChange(ACCOUNT, Plan.TRANSACIONAL, null);

        verify(fixture.repository).schedulePendingChange(ACCOUNT, Plan.TRANSACIONAL, 0, PERIOD_END, null);
    }

    @Test
    void getThrowsWhenAccountHasNoPlanRow() {
        var repository = mock(PlanRepository.class);
        when(repository.find(ACCOUNT)).thenReturn(Optional.empty());
        var service = new PlanService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.get(ACCOUNT)).isInstanceOf(NotFoundException.class);
    }

    private static Fixture fixture(PlatformSubscription current) {
        var repository = mock(PlanRepository.class);
        when(repository.find(ACCOUNT)).thenReturn(Optional.of(current));
        var service = new PlanService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(service, repository);
    }

    private static PlatformSubscription subscription(Plan plan, String providerToken) {
        return new PlatformSubscription(ACCOUNT, plan, plan == Plan.ESCALA ? 19_900 : 0,
                NOW.minusSeconds(86_400), PERIOD_END, PlanStatus.ACTIVE, null, null, null, null, providerToken);
    }

    private record Fixture(PlanService service, PlanRepository repository) {
    }
}
