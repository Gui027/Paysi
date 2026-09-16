package com.paysi.subscription.app;

import java.time.Duration;

/** Régua de retentativa de cobrança recusada: D+1, D+3, D+7, D+14 a partir da primeira falha. */
final class DunningSchedule {
    static final Duration FIRST_RETRY_DELAY = Duration.ofDays(1);

    private static final Duration[] DELAYS_AFTER_ATTEMPT = {
            Duration.ofDays(1), // após a 1ª falha (attempt_count=1): tenta de novo em D+1 (attempt_count=2)
            Duration.ofDays(2), // após a 2ª falha (attempt_count=2): tenta em D+3 (2 dias depois do D+1)
            Duration.ofDays(4), // após a 3ª falha (attempt_count=3): tenta em D+7
            Duration.ofDays(7), // após a 4ª falha (attempt_count=4): tenta em D+14
    };

    private DunningSchedule() {
    }

    /** {@code null} quando a régua se esgotou e a assinatura deve ser cancelada. */
    static Duration nextDelay(int attemptCountAfterThisFailure) {
        int index = attemptCountAfterThisFailure - 1;
        return index >= 0 && index < DELAYS_AFTER_ATTEMPT.length ? DELAYS_AFTER_ATTEMPT[index] : null;
    }
}
