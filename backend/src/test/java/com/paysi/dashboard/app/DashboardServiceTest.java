package com.paysi.dashboard.app;

import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
import com.paysi.dashboard.port.DashboardQueryRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-09T01:30:00Z");
    private static final BalanceView BALANCE = new BalanceView(100, 200, 300, 400, 0, NOW);

    @Test
    void resolvesPresetsFromUtcDayStartWithoutClientSidePeriodMath() {
        var repository = mock(DashboardQueryRepository.class);
        var ledger = mock(LedgerQueryService.class);
        when(repository.sales(eq(SELLER), any(), any())).thenReturn(new SalesSummary(0, 0));
        when(repository.upcomingReceivables(eq(SELLER), any(), eq(5))).thenReturn(List.of());
        when(repository.subscriptions(SELLER)).thenReturn(new SubscriptionSummary(0, 0));
        when(repository.accountAlerts(SELLER)).thenReturn(List.of());
        when(repository.recentSales(SELLER, 5)).thenReturn(List.of());
        when(ledger.balance(SELLER)).thenReturn(BALANCE);
        var service = new DashboardService(repository, ledger, Clock.fixed(NOW, ZoneOffset.UTC));

        DashboardView result = service.get(SELLER, "7d");

        assertThat(result.period().preset()).isEqualTo("7d");
        assertThat(result.period().from()).isEqualTo(Instant.parse("2026-09-03T00:00:00Z"));
        assertThat(result.period().to()).isEqualTo(NOW);
        assertThat(result.salesToday().state()).isEqualTo(DashboardBlock.State.EMPTY);
    }

    @Test
    void returnsIndependentErrorWhenOneBlockFails() {
        var repository = mock(DashboardQueryRepository.class);
        var ledger = mock(LedgerQueryService.class);
        when(repository.sales(eq(SELLER), any(), any())).thenThrow(new RuntimeException("sales down"));
        when(repository.upcomingReceivables(eq(SELLER), any(), eq(5))).thenReturn(List.of());
        when(repository.subscriptions(SELLER)).thenReturn(new SubscriptionSummary(2, 1));
        when(repository.accountAlerts(SELLER)).thenReturn(List.of());
        when(repository.recentSales(SELLER, 5)).thenReturn(List.of());
        when(ledger.balance(SELLER)).thenReturn(BALANCE);
        var service = new DashboardService(repository, ledger, Clock.fixed(NOW, ZoneOffset.UTC));

        DashboardView result = service.get(SELLER, "today");

        assertThat(result.salesToday().state()).isEqualTo(DashboardBlock.State.ERROR);
        assertThat(result.balance().state()).isEqualTo(DashboardBlock.State.SUCCESS);
        assertThat(result.subscriptions().state()).isEqualTo(DashboardBlock.State.SUCCESS);
        assertThat(result.nextReceivables().state()).isEqualTo(DashboardBlock.State.EMPTY);
        assertThat(result.recentSales().state()).isEqualTo(DashboardBlock.State.EMPTY);
    }

    @Test
    void addsDebtAlertWithoutExposingFinancialCalculationToTheClient() {
        var repository = mock(DashboardQueryRepository.class);
        var ledger = mock(LedgerQueryService.class);
        when(repository.sales(eq(SELLER), any(), any())).thenReturn(new SalesSummary(5000, 1));
        when(repository.upcomingReceivables(eq(SELLER), any(), eq(5))).thenReturn(List.of());
        when(repository.subscriptions(SELLER)).thenReturn(new SubscriptionSummary(0, 0));
        when(repository.accountAlerts(SELLER)).thenReturn(List.of());
        when(repository.recentSales(SELLER, 5)).thenReturn(List.of());
        when(ledger.balance(SELLER)).thenReturn(new BalanceView(0, 0, 0, 0, -2500, NOW));
        var service = new DashboardService(repository, ledger, Clock.fixed(NOW, ZoneOffset.UTC));

        DashboardView result = service.get(SELLER, "today");

        assertThat(result.alerts().data()).singleElement().satisfies(alert -> {
            assertThat(alert.id()).isEqualTo("debt");
            assertThat(alert.tone()).isEqualTo("danger");
        });
    }

    @Test
    void rejectsUnknownPeriod() {
        var service = new DashboardService(mock(DashboardQueryRepository.class), mock(LedgerQueryService.class), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.get(SELLER, "quarter"))
                .isInstanceOf(com.paysi.core.error.ValidationException.class)
                .hasMessageContaining("today, 7d ou 30d");
    }
}
