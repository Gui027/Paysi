package com.paysi.admin.dashboard.app;

import com.paysi.admin.dashboard.domain.AdminDashboardView;
import com.paysi.admin.dashboard.port.AdminDashboardRepository;
import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class AdminDashboardService {
    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90, 365);
    private final AdminDashboardRepository repository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminDashboardService(AdminDashboardRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AdminDashboardService(AdminDashboardRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminDashboardView dashboard(Integer rawDays, String rawQuery, Integer rawPage, Integer rawSize) {
        int days = rawDays == null ? 30 : rawDays;
        if (!ALLOWED_PERIODS.contains(days)) {
            throw new ValidationException("ADMIN_PERIOD_INVALID", "Use 7, 30, 90 ou 365 dias", "days");
        }
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() > 200) throw new ValidationException("ADMIN_QUERY_INVALID", "Busca muito longa", "q");
        int page = rawPage == null || rawPage < 0 ? 0 : rawPage;
        int size = rawSize == null ? 20 : Math.max(1, Math.min(rawSize, 100));
        LocalDate lastDay = LocalDate.now(clock);
        LocalDate firstDay = lastDay.minusDays(days - 1L);
        var periodStart = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC);
        long total = repository.countAccounts(query);
        int totalPages = total == 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new AdminDashboardView(repository.summary(periodStart),
                repository.revenueTimeline(firstDay, lastDay), repository.accounts(query, size, page * size),
                page, size, total, totalPages);
    }
}
