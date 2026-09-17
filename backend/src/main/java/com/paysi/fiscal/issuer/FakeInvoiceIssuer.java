package com.paysi.fiscal.issuer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * BE-14.1: emissor fiscal falso, no mesmo molde de {@code FakePaymentProvider} — não há
 * contrato de parceiro fiscal disponível, então o desenvolvimento e os testes rodam contra
 * um cenário determinístico injetado por propriedade.
 */
@Component
@ConditionalOnProperty(name = "paysi.fiscal.issuer", havingValue = "fake")
public class FakeInvoiceIssuer implements InvoiceIssuer {
    private final FakeIssuerOutcome outcome;

    @org.springframework.beans.factory.annotation.Autowired
    public FakeInvoiceIssuer(@Value("${paysi.fiscal.fake-outcome:APPROVED}") String outcome) {
        this(FakeIssuerOutcome.valueOf(outcome.toUpperCase()));
    }

    FakeInvoiceIssuer(FakeIssuerOutcome outcome) {
        this.outcome = outcome;
    }

    @Override
    public IssuerValidationResult validate(IssuerCredentials credentials) {
        return switch (outcome) {
            case APPROVED -> new IssuerValidationResult(true, null);
            case REJECTED -> new IssuerValidationResult(false, "ISSUER_CREDENTIALS_REJECTED");
            case TIMEOUT -> new IssuerValidationResult(false, "ISSUER_TIMEOUT");
        };
    }

    @Override
    public InvoiceIssueResult issue(InvoiceIssueRequest request) {
        return switch (outcome) {
            case APPROVED -> new InvoiceIssueResult(true, "fake_nfse_" + request.invoiceId(),
                    "NFSe-" + request.invoiceId().toString().substring(0, 8).toUpperCase(),
                    "https://fake.paysi/nfse/" + request.invoiceId(), null);
            case REJECTED -> new InvoiceIssueResult(false, null, null, null, "ISSUER_CREDENTIALS_REJECTED");
            case TIMEOUT -> new InvoiceIssueResult(false, null, null, null, "ISSUER_TIMEOUT");
        };
    }

    @Override
    public InvoiceCancelResult cancel(InvoiceCancelRequest request) {
        return switch (outcome) {
            case APPROVED -> new InvoiceCancelResult(true, null);
            case REJECTED -> new InvoiceCancelResult(false, "ISSUER_CANCEL_REJECTED");
            case TIMEOUT -> new InvoiceCancelResult(false, "ISSUER_TIMEOUT");
        };
    }
}
