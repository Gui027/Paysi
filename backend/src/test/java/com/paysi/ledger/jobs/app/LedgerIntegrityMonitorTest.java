package com.paysi.ledger.jobs.app;

import com.paysi.ledger.jobs.port.IntegrityRepository;
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
        when(repository.violations(failingView)).thenReturn(1L);

        var violations = new LedgerIntegrityMonitor(repository).inspect();

        assertThat(violations).containsExactly(new IntegrityViolation(failingView, 1));
        LedgerIntegrityMonitor.VIEWS.forEach(view -> verify(repository).violations(view));
    }

    static Stream<String> views() {
        return LedgerIntegrityMonitor.VIEWS.stream();
    }
}
