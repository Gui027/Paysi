package com.paysi.ledger.app;

import com.paysi.ledger.domain.*;
import com.paysi.ledger.port.ChargeSaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * BE-07.1 (parte que faltava): credita vendedor e afiliado quando uma cobrança é
 * confirmada. Até aqui nada creditava ninguém no razão — nem o vendedor (por
 * nenhum caminho) nem o afiliado nas confirmações assíncronas de Pix/boleto —, o
 * que também deixava o reembolso (BE-12.1) sem ter o que reverter. Chamado tanto
 * pela aprovação síncrona do cartão quanto pela confirmação assíncrona (inbox do
 * provedor), e é seguro reprocessar o mesmo chargeId (replay, corrida).
 */
@Service
public class SaleLedgerService {
    /** SYS_CLEARING (V029): origem do dinheiro que entra do provedor. */
    private static final UUID SYS_CLEARING = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    /** SYS_PLATFORM_REVENUE (V029): taxa da plataforma sobre a venda. */
    private static final UUID SYS_PLATFORM_REVENUE = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final ChargeSaleRepository repository;
    private final LedgerService ledger;

    public SaleLedgerService(ChargeSaleRepository repository, LedgerService ledger) {
        this.repository = repository;
        this.ledger = ledger;
    }

    @Transactional
    public void creditForCharge(UUID chargeId, Instant confirmedAt) {
        var sale = repository.findChargeSale(chargeId).orElseThrow(() ->
                new IllegalStateException("Cobrança " + chargeId + " não encontrada para liquidar a venda"));
        Instant releaseAt = confirmedAt.plus(Duration.ofDays(sale.guaranteeDays()));

        if (sale.sellerAmountCents() > 0 || sale.platformFeeCents() > 0) {
            var entries = new java.util.ArrayList<LedgerEntry>();
            entries.add(new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.DEBIT,
                    sale.sellerAmountCents() + sale.platformFeeCents(), Origin.SALE, null));
            if (sale.sellerAmountCents() > 0) {
                entries.add(new LedgerEntry(sale.sellerId(), Bucket.GUARANTEE, Direction.CREDIT,
                        sale.sellerAmountCents(), Origin.SALE, releaseAt));
            }
            if (sale.platformFeeCents() > 0) {
                entries.add(new LedgerEntry(SYS_PLATFORM_REVENUE, Bucket.SYSTEM, Direction.CREDIT,
                        sale.platformFeeCents(), Origin.SALE, null));
            }
            ledger.write(new LedgerCommand(TransactionType.SALE,
                    new LedgerReference(ReferenceType.CHARGE, chargeId + ":sale"), "Venda confirmada",
                    List.copyOf(entries)));
        }

        if (sale.affiliateId() != null && sale.affiliateFeeCents() > 0) {
            var entries = List.of(
                    new LedgerEntry(SYS_CLEARING, Bucket.SYSTEM, Direction.DEBIT, sale.affiliateFeeCents(),
                            Origin.COMMISSION, null),
                    new LedgerEntry(sale.affiliateId(), Bucket.GUARANTEE, Direction.CREDIT,
                            sale.affiliateFeeCents(), Origin.COMMISSION, releaseAt));
            ledger.write(new LedgerCommand(TransactionType.COMMISSION,
                    new LedgerReference(ReferenceType.CHARGE, chargeId + ":commission"), "Comissão de afiliado",
                    entries));
        }
    }
}
