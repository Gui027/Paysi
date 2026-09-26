package com.paysi.dashboard.app;

import com.paysi.core.error.ValidationException;
import com.paysi.dashboard.app.AffiliateDashboardView.Earnings;
import com.paysi.dashboard.port.AffiliateDashboardRepository;
import com.paysi.dashboard.port.DashboardQueryRepository;
import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Dashboard do afiliado. Cada bloco falha sozinho: um erro em um deles não derruba a tela. */
@Service
public class AffiliateDashboardService {
    private static final int MAX_ITEMS = 5;

    private final AffiliateDashboardRepository repository;
    private final DashboardQueryRepository accountRepository;
    private final LedgerQueryService ledger;
    private final Clock clock;

    @Autowired
    public AffiliateDashboardService(AffiliateDashboardRepository repository, DashboardQueryRepository accountRepository, LedgerQueryService ledger) {
        this(repository, accountRepository, ledger, Clock.systemUTC());
    }

    AffiliateDashboardService(AffiliateDashboardRepository repository, DashboardQueryRepository accountRepository, LedgerQueryService ledger, Clock clock) {
        this.repository = repository;
        this.accountRepository = accountRepository;
        this.ledger = ledger;
        this.clock = clock;
    }

    public AffiliateDashboardView get(UUID affiliateId, String rawPreset) {
        Instant now = clock.instant();
        DashboardPeriod period = period(rawPreset, now);
        return new AffiliateDashboardView(
                period,
                block(() -> earnings(affiliateId, period), value -> value.sales() == 0 && value.clicks() == 0 && value.commissionCents() == 0),
                block(() -> ledger.balance(affiliateId), value -> false),
                block(() -> repository.upcomingCommissions(affiliateId, now, MAX_ITEMS), List::isEmpty),
                block(() -> repository.affiliations(affiliateId), value -> value.active() == 0 && value.pending() == 0),
                block(() -> {
                    BalanceView balance = ledger.balance(affiliateId);
                    var alerts = new ArrayList<>(accountRepository.accountAlerts(affiliateId));
                    if (balance.debt() != 0) {
                        alerts.add(new DashboardAlert("debt", "danger", "Conta com débito em aberto",
                                "Existe um saldo devedor que será compensado nas próximas comissões.", "/saldo"));
                    }
                    return List.copyOf(alerts);
                }, List::isEmpty),
                block(() -> repository.topProducts(affiliateId, period.from(), period.to(), MAX_ITEMS), List::isEmpty),
                block(() -> repository.recentCommissions(affiliateId, MAX_ITEMS), List::isEmpty));
    }

    private Earnings earnings(UUID affiliateId, DashboardPeriod period) {
        var sales = repository.sales(affiliateId, period.from(), period.to());
        long clicks = repository.clicks(affiliateId, period.from(), period.to());
        return new Earnings(sales.commissionCents(), sales.count(), clicks, conversion(sales.count(), clicks));
    }

    /** Vendas por clique em porcentagem com uma casa; sem cliques não há taxa a mostrar. */
    static String conversion(long sales, long clicks) {
        if (clicks <= 0) return null;
        return BigDecimal.valueOf(sales * 100).divide(BigDecimal.valueOf(clicks), 1, RoundingMode.HALF_UP).toPlainString();
    }

    private static <T> DashboardBlock<T> block(Supplier<T> loader, Predicate<T> empty) {
        try {
            T value = loader.get();
            return empty.test(value) ? DashboardBlock.empty() : DashboardBlock.success(value);
        } catch (RuntimeException error) {
            return DashboardBlock.error("DASHBOARD_BLOCK_UNAVAILABLE", "Este bloco está temporariamente indisponível.");
        }
    }

    private static DashboardPeriod period(String rawPreset, Instant now) {
        String preset = rawPreset == null || rawPreset.isBlank() ? "today" : rawPreset.strip().toLowerCase();
        var today = now.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        return switch (preset) {
            case "today" -> new DashboardPeriod("today", today, now);
            case "7d" -> new DashboardPeriod("7d", today.minus(6, ChronoUnit.DAYS), now);
            case "30d" -> new DashboardPeriod("30d", today.minus(29, ChronoUnit.DAYS), now);
            default -> throw new ValidationException("INVALID_PERIOD", "Período deve ser today, 7d ou 30d", "period");
        };
    }
}
