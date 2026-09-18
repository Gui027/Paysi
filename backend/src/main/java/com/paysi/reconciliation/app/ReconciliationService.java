package com.paysi.reconciliation.app;

import com.paysi.core.error.ValidationException;
import com.paysi.reconciliation.domain.ReconciliationEntry;
import com.paysi.reconciliation.port.ReconciliationRepository;
import com.paysi.reconciliation.port.ReconciliationRepository.InternalTotal;
import com.paysi.reconciliation.port.ReconciliationRepository.StatementLine;
import com.paysi.webhook.app.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * BE-14.4: compara o livro-razão com o extrato do provedor por referência (RNF-016).
 * Não existe API real de extrato do PSP — o extrato é importado explicitamente
 * ({@link #importStatement}) para uma tabela de staging, e {@link #reconcile} é o
 * job diário que compara. Reexecutável: upsert por {@code (data, referência)} e
 * alerta apenas na primeira vez que uma linha vira DIVERGED naquele dia.
 */
@Service
public class ReconciliationService {
    /** Conta de sistema usada quando a divergência não tem cobrança interna correspondente
     * (linha só existe no extrato do provedor) — não há vendedor a quem atribuir o evento. */
    private static final UUID PLATFORM_ACCOUNT = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private final ReconciliationRepository repository;
    private final OutboxService outbox;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ReconciliationService(ReconciliationRepository repository, OutboxService outbox) {
        this(repository, outbox, Clock.systemUTC());
    }

    ReconciliationService(ReconciliationRepository repository, OutboxService outbox, Clock clock) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public void importStatement(List<ImportStatementLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("RECONCILIATION_STATEMENT_EMPTY", "Informe ao menos uma linha de extrato",
                    "lines");
        }
        for (ImportStatementLine line : lines) {
            if (line.providerReference() == null || line.providerReference().isBlank()) {
                throw new ValidationException("RECONCILIATION_REFERENCE_REQUIRED",
                        "Referência do provedor é obrigatória", "providerReference");
            }
            if (line.amountCents() <= 0) {
                throw new ValidationException("RECONCILIATION_AMOUNT_INVALID", "Valor deve ser positivo",
                        "amountCents");
            }
            if (line.statementDate() == null) {
                throw new ValidationException("RECONCILIATION_DATE_REQUIRED", "Data do extrato é obrigatória",
                        "statementDate");
            }
        }
        List<StatementLine> mapped = lines.stream()
                .map(line -> new StatementLine(line.providerReference().strip(), line.amountCents(),
                        line.statementDate()))
                .toList();
        repository.importStatementLines(mapped, clock.instant());
    }

    /** Job diário (e reexecutável manualmente) — compara ledger x extrato para {@code date}. */
    @Transactional
    public ReconciliationEntry.Report reconcile(LocalDate date) {
        if (date == null) {
            throw new ValidationException("RECONCILIATION_DATE_REQUIRED", "Informe a data a conciliar", "date");
        }
        List<InternalTotal> internal = repository.internalTotals(date);
        List<StatementLine> statement = repository.statementEntries(date);

        Map<String, Long> internalByRef = new HashMap<>();
        Map<String, UUID> sellerByRef = new HashMap<>();
        for (InternalTotal total : internal) {
            internalByRef.merge(total.providerReference(), total.amountCents(), Long::sum);
            sellerByRef.putIfAbsent(total.providerReference(), total.sellerId());
        }
        Map<String, Long> providerByRef = new HashMap<>();
        for (StatementLine line : statement) {
            providerByRef.merge(line.providerReference(), line.amountCents(), Long::sum);
        }

        Set<String> references = new TreeSet<>();
        references.addAll(internalByRef.keySet());
        references.addAll(providerByRef.keySet());

        List<ReconciliationEntry> entries = new ArrayList<>();
        Instant now = clock.instant();
        for (String reference : references) {
            long internalCents = internalByRef.getOrDefault(reference, 0L);
            long providerCents = providerByRef.getOrDefault(reference, 0L);
            ReconciliationEntry entry = ReconciliationEntry.compare(reference, internalCents, providerCents);
            entries.add(entry);

            var upserted = repository.upsert(date, reference, entry.internalCents(), entry.providerCents(),
                    entry.differenceCents(), entry.status(), now);
            if (entry.diverged() && upserted.previousAlertedAt() == null) {
                UUID sellerId = sellerByRef.getOrDefault(reference, PLATFORM_ACCOUNT);
                outbox.append(sellerId, "reconciliation.diverged",
                        new DivergedEvent(date, reference, entry.internalCents(), entry.providerCents(),
                                entry.differenceCents()));
                repository.markAlerted(upserted.id(), now);
            }
        }
        return new ReconciliationEntry.Report(date, List.copyOf(entries));
    }

    private record DivergedEvent(LocalDate reconDate, String providerReference, long internalCents,
                                  long providerCents, long differenceCents) {
    }
}
