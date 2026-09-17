package com.paysi.fiscal.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository {
    /**
     * Insere a nota como QUEUED, chamada dentro da MESMA transação da confirmação do
     * pagamento (RF-113: só grava localmente, nunca chama o emissor aqui). Idempotente: uma
     * segunda chamada para a mesma cobrança não duplica (índice único em {@code charge_id}
     * para status diferente de FAILED).
     */
    void enqueue(UUID invoiceId, UUID chargeId, UUID sellerId);

    Optional<ClaimedInvoice> claimDue(Instant now);

    /** RF-112: pede cancelamento de uma nota já emitida para a cobrança; no-op se não houver uma. */
    boolean requestCancellation(UUID chargeId);

    void markIssued(UUID invoiceId, String providerRef, String number, String pdfUrl, Instant issuedAt,
                     int attemptCount);

    void markIssueRetry(UUID invoiceId, String error, int attemptCount, Instant nextRetryAt);

    void markIssueFailedTerminal(UUID invoiceId, String error, int attemptCount);

    void markCanceled(UUID invoiceId, int attemptCount);

    void markCancelRetry(UUID invoiceId, String error, int attemptCount, Instant nextRetryAt);

    void markCancelFailedTerminal(UUID invoiceId, String error, int attemptCount);

    Optional<InvoiceView> findByChargeForSeller(UUID sellerId, UUID chargeId);

    record ClaimedInvoice(UUID id, UUID chargeId, UUID sellerId, String status, String providerRef,
                           long amountCents, int attemptCount) {
    }

    record InvoiceView(UUID id, UUID chargeId, String status, String number, String pdfUrl, int attemptCount,
                        String error) {
    }
}
