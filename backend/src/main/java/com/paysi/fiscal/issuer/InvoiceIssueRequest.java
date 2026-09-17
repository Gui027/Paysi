package com.paysi.fiscal.issuer;

import java.util.UUID;

public record InvoiceIssueRequest(UUID invoiceId, UUID chargeId, long amountCents, IssuerCredentials credentials) {
}
