package com.paysi.admin.dashboard.app;

import com.paysi.admin.dashboard.domain.AdminDashboardView;
import com.paysi.admin.dashboard.port.AdminDashboardRepository;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AdminDashboardServiceTest {
    private final AdminDashboardRepository repository = mock(AdminDashboardRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
    private final AdminDashboardService service = new AdminDashboardService(repository, clock);

    @Test
    void buildsDashboardWithUtcPeriodPaginationAndSearch() {
        var summary = new AdminDashboardView.Summary(100_000, 25_000, 4, 3, 2);
        when(repository.summary(Instant.parse("2026-10-12T00:00:00Z"))).thenReturn(summary);
        when(repository.revenueTimeline(LocalDate.parse("2026-10-12"), LocalDate.parse("2026-11-10")))
                .thenReturn(List.of());
        when(repository.countAccounts("ana")).thenReturn(41L);
        when(repository.accounts("ana", 20, 20)).thenReturn(List.of());

        var result = service.dashboard(30, " ana ", 1, 20);

        assertThat(result.summary()).isEqualTo(summary);
        assertThat(result.totalAccounts()).isEqualTo(41);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(1);
    }

    @Test
    void validatesPeriodAndCapsPageSize() {
        assertThatThrownBy(() -> service.dashboard(14, "", 0, 20)).isInstanceOf(ValidationException.class);
        when(repository.countAccounts("")).thenReturn(0L);
        service.dashboard(7, "", -1, 1000);
        verify(repository).accounts("", 100, 0);
    }
}
