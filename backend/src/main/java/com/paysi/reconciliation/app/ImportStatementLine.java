package com.paysi.reconciliation.app;

import java.time.LocalDate;

public record ImportStatementLine(String providerReference, long amountCents, LocalDate statementDate) {
}
