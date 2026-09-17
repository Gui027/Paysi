package com.paysi.fiscal.app;

import com.paysi.fiscal.issuer.InvoiceCancelRequest;
import com.paysi.fiscal.issuer.InvoiceIssueRequest;
import com.paysi.fiscal.issuer.InvoiceIssuer;
import com.paysi.fiscal.issuer.IssuerCredentials;
import com.paysi.fiscal.port.FiscalProfileRepository;
import com.paysi.fiscal.port.InvoiceRepository;
import com.paysi.fiscal.port.InvoiceRepository.ClaimedInvoice;
import com.paysi.webhook.app.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processador assíncrono da fila fiscal (ADR-11 + RF-113): roda fora do caminho de
 * confirmação do pagamento, reivindicando uma nota por vez com {@code FOR UPDATE SKIP
 * LOCKED} (mesmo idioma de {@code JdbcSubscriptionRepository}) para permitir mais de uma
 * instância sem duplicar chamada ao emissor. Emissão (QUEUED) e cancelamento
 * (CANCEL_REQUESTED, RF-112) compartilham a mesma fila e a mesma régua de retentativa.
 */
@Service
public class InvoiceIssuingService {
    private final InvoiceRepository invoices;
    private final FiscalProfileRepository profiles;
    private final InvoiceIssuer issuer;
    private final OutboxService outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public InvoiceIssuingService(InvoiceRepository invoices, FiscalProfileRepository profiles, InvoiceIssuer issuer,
                                  OutboxService outbox) {
        this(invoices, profiles, issuer, outbox, Clock.systemUTC());
    }

    InvoiceIssuingService(InvoiceRepository invoices, FiscalProfileRepository profiles, InvoiceIssuer issuer,
                          OutboxService outbox, Clock clock) {
        this.invoices = invoices;
        this.profiles = profiles;
        this.issuer = issuer;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** Processa até {@code limit} notas devidas; devolve quantas foram tratadas (sucesso ou falha final/retentável). */
    public int processBatch(int limit) {
        int processed = 0;
        for (int i = 0; i < limit; i++) {
            if (!processNext()) break;
            processed++;
        }
        return processed;
    }

    @Transactional
    public boolean processNext() {
        var claimed = invoices.claimDue(clock.instant());
        if (claimed.isEmpty()) return false;
        var invoice = claimed.get();
        if ("CANCEL_REQUESTED".equals(invoice.status())) {
            processCancel(invoice);
        } else {
            processIssue(invoice);
        }
        return true;
    }

    private void processIssue(ClaimedInvoice invoice) {
        var credentials = profiles.findByAccount(invoice.sellerId())
                .filter(profile -> profile.validatedAt() != null)
                .map(profile -> new IssuerCredentials(profile.municipalityCode(), profile.municipalRegistration(),
                        profile.serviceItem(), profile.taxBps(), profile.taxRegime(), profile.credentialRef()));
        if (credentials.isEmpty()) {
            failIssueTerminal(invoice, "FISCAL_PROFILE_NOT_VALIDATED");
            return;
        }
        var result = issuer.issue(new InvoiceIssueRequest(invoice.id(), invoice.chargeId(), invoice.amountCents(),
                credentials.get()));
        int attempt = invoice.attemptCount() + 1;
        if (result.succeeded()) {
            Instant now = clock.instant();
            invoices.markIssued(invoice.id(), result.providerRef(), result.number(), result.pdfUrl(), now, attempt);
            outbox.append(invoice.sellerId(), "invoice.issued",
                    new InvoiceIssuedEvent(invoice.chargeId(), invoice.id(), result.number(), result.pdfUrl()));
            return;
        }
        if (InvoiceRetrySchedule.exhausted(attempt)) {
            failIssueTerminal(invoice, result.error());
        } else {
            invoices.markIssueRetry(invoice.id(), result.error(), attempt,
                    clock.instant().plus(InvoiceRetrySchedule.delayFor(attempt)));
        }
    }

    private void failIssueTerminal(ClaimedInvoice invoice, String error) {
        int attempt = invoice.attemptCount() + 1;
        invoices.markIssueFailedTerminal(invoice.id(), error, attempt);
        outbox.append(invoice.sellerId(), "invoice.failed",
                new InvoiceFailedEvent(invoice.chargeId(), invoice.id(), error));
    }

    private void processCancel(ClaimedInvoice invoice) {
        var result = issuer.cancel(new InvoiceCancelRequest(invoice.id(), invoice.providerRef()));
        int attempt = invoice.attemptCount() + 1;
        if (result.succeeded()) {
            invoices.markCanceled(invoice.id(), attempt);
            return;
        }
        if (InvoiceRetrySchedule.exhausted(attempt)) {
            invoices.markCancelFailedTerminal(invoice.id(), result.error(), attempt);
            outbox.append(invoice.sellerId(), "invoice.failed",
                    new InvoiceFailedEvent(invoice.chargeId(), invoice.id(), result.error()));
        } else {
            invoices.markCancelRetry(invoice.id(), result.error(), attempt,
                    clock.instant().plus(InvoiceRetrySchedule.delayFor(attempt)));
        }
    }

    private record InvoiceIssuedEvent(UUID chargeId, UUID invoiceId, String number, String pdfUrl) {
    }

    private record InvoiceFailedEvent(UUID chargeId, UUID invoiceId, String error) {
    }
}
