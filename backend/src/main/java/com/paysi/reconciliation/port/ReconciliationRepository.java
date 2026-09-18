package com.paysi.reconciliation.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ReconciliationRepository {

    /** Uma linha por referência do provedor paga naquele dia, com o vendedor dono da cobrança. */
    List<InternalTotal> internalTotals(LocalDate date);

    /** Extrato já importado para aquele dia (ver {@link #importStatementLines}). */
    List<StatementLine> statementEntries(LocalDate date);

    /**
     * Reexecutável: reimportar a mesma referência apenas atualiza o valor
     * (não existe API real de extrato — a importação é um passo explícito).
     */
    void importStatementLines(List<StatementLine> lines, Instant importedAt);

    /**
     * Upsert por {@code (recon_date, provider_reference)} — é isso que torna o job
     * reexecutável sem duplicar linha. Devolve o {@code alerted_at} anterior (antes
     * deste upsert) para o chamador decidir se já alertou essa divergência.
     */
    UpsertResult upsert(LocalDate date, String providerReference, long internalCents, long providerCents,
                         long differenceCents, String status, Instant now);

    void markAlerted(UUID entryId, Instant alertedAt);

    record InternalTotal(String providerReference, long amountCents, UUID sellerId) {}

    record StatementLine(String providerReference, long amountCents, LocalDate statementDate) {}

    record UpsertResult(UUID id, Instant previousAlertedAt) {}
}
