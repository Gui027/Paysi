package com.paysi.reconciliation.domain;

import java.time.LocalDate;

public record ReconciliationEntry(String providerReference, long internalCents, long providerCents,
                                   long differenceCents, String status) {
    public static final String MATCHED = "MATCHED";
    public static final String DIVERGED = "DIVERGED";

    /** RNF-016: divergência acima de 1 centavo alerta; exatamente 1 centavo não. */
    public static final long ALERT_THRESHOLD_CENTS = 1;

    public static ReconciliationEntry compare(String providerReference, long internalCents, long providerCents) {
        long difference = internalCents - providerCents;
        String status = Math.abs(difference) > ALERT_THRESHOLD_CENTS ? DIVERGED : MATCHED;
        return new ReconciliationEntry(providerReference, internalCents, providerCents, difference, status);
    }

    public boolean diverged() {
        return DIVERGED.equals(status);
    }

    public record Report(LocalDate reconDate, java.util.List<ReconciliationEntry> entries) {
    }
}
