package com.paysi.dashboard.app;

import com.paysi.core.error.ValidationException;
import com.paysi.dashboard.app.AffiliateDashboardView.AffiliationCounts;
import com.paysi.dashboard.port.AffiliateDashboardRepository;
import com.paysi.dashboard.port.DashboardQueryRepository;
import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
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

class AffiliateDashboardServiceTest {
    private static final UUID AFFILIATE = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-26T15:30:00Z");
    private static final BalanceView BALANCE = new BalanceView(0, 0, 0, 4990, 0, NOW);

    private final AffiliateDashboardRepository repository = mock(AffiliateDashboardRepository.class);
    private final DashboardQueryRepository account = mock(DashboardQueryRepository.class);
    private final LedgerQueryService ledger = mock(LedgerQueryService.class);
    private final AffiliateDashboardService service = new AffiliateDashboardService(repository, account, ledger, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void earningsCarryCommissionSalesClicksAndConversionComputedOnTheServer() {
        when(repository.sales(eq(AFFILIATE), any(), any())).thenReturn(new AffiliateDashboardRepository.Sales(3, 1497));
        when(repository.clicks(eq(AFFILIATE), any(), any())).thenReturn(40L);
        when(repository.affiliations(AFFILIATE)).thenReturn(new AffiliationCounts(2, 1));
        when(ledger.balance(AFFILIATE)).thenReturn(BALANCE);
        when(account.accountAlerts(AFFILIATE)).thenReturn(List.of());

        var view = service.get(AFFILIATE, "7d");

        assertThat(view.period().preset()).isEqualTo("7d");
        assertThat(view.period().from()).isEqualTo(Instant.parse("2026-09-20T00:00:00Z"));
        var earnings = view.earnings().data();
        assertThat(earnings.commissionCents()).isEqualTo(1497);
        assertThat(earnings.sales()).isEqualTo(3);
        assertThat(earnings.clicks()).isEqualTo(40);
        assertThat(earnings.conversionPercent()).isEqualTo("7.5");
        assertThat(view.balance().data().available()).isEqualTo(4990);
        assertThat(view.affiliations().data().active()).isEqualTo(2);
        assertThat(view.alerts().state()).isEqualTo(DashboardBlock.State.EMPTY);
    }

    @Test
    void conversionIsAbsentWithoutClicksAndRoundsToOneDecimal() {
        assertThat(AffiliateDashboardService.conversion(5, 0)).isNull();
        assertThat(AffiliateDashboardService.conversion(1, 3)).isEqualTo("33.3");
        assertThat(AffiliateDashboardService.conversion(0, 10)).isEqualTo("0.0");
    }

    @Test
    void emptyAccountShowsEmptyBlocksAndDebtBecomesAnAlert() {
        when(repository.sales(eq(AFFILIATE), any(), any())).thenReturn(new AffiliateDashboardRepository.Sales(0, 0));
        when(repository.clicks(eq(AFFILIATE), any(), any())).thenReturn(0L);
        when(repository.affiliations(AFFILIATE)).thenReturn(new AffiliationCounts(0, 0));
        when(repository.topProducts(eq(AFFILIATE), any(), any(), eq(5))).thenReturn(List.of());
        when(repository.recentCommissions(AFFILIATE, 5)).thenReturn(List.of());
        when(repository.upcomingCommissions(eq(AFFILIATE), any(), eq(5))).thenReturn(List.of());
        when(ledger.balance(AFFILIATE)).thenReturn(new BalanceView(0, 0, 0, 0, 500, NOW));
        when(account.accountAlerts(AFFILIATE)).thenReturn(List.of());

        var view = service.get(AFFILIATE, null);

        assertThat(view.period().preset()).isEqualTo("today");
        assertThat(view.earnings().state()).isEqualTo(DashboardBlock.State.EMPTY);
        assertThat(view.topProducts().state()).isEqualTo(DashboardBlock.State.EMPTY);
        assertThat(view.recentCommissions().state()).isEqualTo(DashboardBlock.State.EMPTY);
        assertThat(view.alerts().data()).extracting(DashboardAlert::id).containsExactly("debt");
    }

    @Test
    void aFailingBlockDoesNotBreakTheOthersAndTheperiodIsValidated() {
        when(repository.sales(eq(AFFILIATE), any(), any())).thenThrow(new IllegalStateException("db"));
        when(repository.affiliations(AFFILIATE)).thenReturn(new AffiliationCounts(1, 0));
        when(ledger.balance(AFFILIATE)).thenReturn(BALANCE);
        when(account.accountAlerts(AFFILIATE)).thenReturn(List.of());

        var view = service.get(AFFILIATE, "30d");

        assertThat(view.earnings().state()).isEqualTo(DashboardBlock.State.ERROR);
        assertThat(view.affiliations().state()).isEqualTo(DashboardBlock.State.SUCCESS);
        assertThatThrownBy(() -> service.get(AFFILIATE, "1y")).isInstanceOf(ValidationException.class);
    }
}
