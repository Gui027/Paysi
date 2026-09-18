package com.paysi.ledger.jobs.app;

import com.paysi.ledger.jobs.port.IntegrityRepository;
import com.paysi.observability.alert.app.AlertService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LedgerIntegrityMonitorTest {
    @ParameterizedTest
    @MethodSource("views")
    void everyInjectedDefectActivatesItsIntegrityView(String failingView) {
        var repository = mock(IntegrityRepository.class);
        var alerts = mock(AlertService.class);
        when(repository.violations(failingView)).thenReturn(1L);

        var violations = new LedgerIntegrityMonitor(repository, alerts, new SimpleMeterRegistry()).inspect();

        assertThat(violations).containsExactly(new IntegrityViolation(failingView, 1));
        LedgerIntegrityMonitor.VIEWS.forEach(view -> verify(repository).violations(view));
    }

    @Test
    void everyViolatedViewRaisesItsOwnCriticalAlert() {
        var repository = mock(IntegrityRepository.class);
        var alerts = mock(AlertService.class);
        when(repository.violations("v_check_negative_user_buckets")).thenReturn(3L);

        new LedgerIntegrityMonitor(repository, alerts, new SimpleMeterRegistry()).inspect();

        verify(alerts).raise(eq("LEDGER_INTEGRITY_VIOLATION"), eq("CRITICAL"), any());
        verifyNoMoreInteractions(alerts);
    }

    @Test
    void noViolationRaisesNoAlert() {
        var repository = mock(IntegrityRepository.class);
        var alerts = mock(AlertService.class);
        when(repository.violations(anyString())).thenReturn(0L);

        var violations = new LedgerIntegrityMonitor(repository, alerts, new SimpleMeterRegistry()).inspect();

        assertThat(violations).isEmpty();
        verifyNoInteractions(alerts);
    }

    static Stream<String> views() {
        return LedgerIntegrityMonitor.VIEWS.stream();
    }
}
