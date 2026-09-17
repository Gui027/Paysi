package com.paysi.fiscal.app;

import java.time.Duration;

/**
 * "Retry exponencial" da fila fiscal, no mesmo idioma de
 * {@code ProviderEventService.RETRY_DELAYS}: um array de atrasos crescentes indexado pela
 * tentativa, esgotado o qual a nota vai para o estado terminal (FAILED/CANCEL_FAILED) e o
 * vendedor é alertado (RF-113).
 */
final class InvoiceRetrySchedule {
    static final Duration[] RETRY_DELAYS = {
            Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2),
            Duration.ofHours(12), Duration.ofHours(24)
    };

    private InvoiceRetrySchedule() {
    }

    static boolean exhausted(int attemptCount) {
        return attemptCount >= RETRY_DELAYS.length;
    }

    static Duration delayFor(int attemptCount) {
        return RETRY_DELAYS[Math.min(attemptCount - 1, RETRY_DELAYS.length - 1)];
    }
}
