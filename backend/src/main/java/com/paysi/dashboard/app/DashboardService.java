package com.paysi.dashboard.app;

import com.paysi.core.error.ValidationException;
import com.paysi.ledger.query.app.BalanceView;
import com.paysi.ledger.query.app.LedgerQueryService;
import com.paysi.dashboard.port.DashboardQueryRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class DashboardService {
    private static final int MAX_ITEMS = 5;
    private final DashboardQueryRepository repository;
    private final LedgerQueryService ledger;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DashboardService(DashboardQueryRepository repository, LedgerQueryService ledger) {
        this(repository, ledger, Clock.systemUTC());
    }

    DashboardService(DashboardQueryRepository repository, LedgerQueryService ledger, Clock clock) {
        this.repository = repository;
        this.ledger = ledger;
        this.clock = clock;
    }

    public DashboardView get(UUID sellerId, String rawPreset) {
        Instant now = clock.instant();
        DashboardPeriod period = period(rawPreset, now);
        return new DashboardView(
                period,
                block(() -> repository.sales(sellerId, period.from(), period.to()), value -> value.count() == 0),
                block(() -> ledger.balance(sellerId), value -> false),
                block(() -> repository.upcomingReceivables(sellerId, now, MAX_ITEMS), List::isEmpty),
                block(() -> repository.subscriptions(sellerId), value -> value.active() == 0 && value.pastDue() == 0),
                block(() -> {
                    BalanceView balance = ledger.balance(sellerId);
                    var alerts = new java.util.ArrayList<>(repository.accountAlerts(sellerId));
                    if (balance.debt() != 0) {
                        alerts.add(new DashboardAlert("debt", "danger", "Conta com débito em aberto",
                                "Existe um saldo devedor que será compensado nas próximas vendas.", "/saldo"));
                    }
                    return List.copyOf(alerts);
                }, List::isEmpty),
                block(() -> repository.recentSales(sellerId, MAX_ITEMS), List::isEmpty));
    }

    private static <T> DashboardBlock<T> block(Supplier<T> loader, java.util.function.Predicate<T> empty) {
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
