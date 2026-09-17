package com.paysi.fiscal.issuer;

import java.util.UUID;

public record InvoiceCancelRequest(UUID invoiceId, String providerRef) {
}
