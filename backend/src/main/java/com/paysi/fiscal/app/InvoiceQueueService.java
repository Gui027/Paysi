package com.paysi.fiscal.app;

import com.paysi.fiscal.port.FiscalProfileRepository;
import com.paysi.fiscal.port.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * RF-113: chamado de dentro da MESMA transação em que a venda é confirmada
 * ({@code SaleLedgerService.creditForCharge}), o único lugar que já sabe "uma venda foi
 * confirmada" sem duplicar essa lógica nos quatro pontos de chamada (cartão síncrono,
 * confirmação assíncrona de Pix/boleto, ciclo e retentativa de assinatura).
 *
 * <p>Isto é apenas um INSERT local — nunca chama o emissor fiscal aqui. Se o vendedor não
 * tem perfil fiscal validado, não há nota a enfileirar (produto não exige emissão
 * automatizada ainda). Qualquer erro é engolido: a confirmação do pagamento não pode
 * falhar por causa da fila fiscal (RF-113), mesmo com o emissor fora do ar — que nem chega
 * a ser chamado neste caminho.
 */
@Service
public class InvoiceQueueService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceQueueService.class);

    private final FiscalProfileRepository profiles;
    private final InvoiceRepository invoices;

    public InvoiceQueueService(FiscalProfileRepository profiles, InvoiceRepository invoices) {
        this.profiles = profiles;
        this.invoices = invoices;
    }

    public void enqueueAfterSale(UUID chargeId, UUID sellerId) {
        try {
            boolean validated = profiles.findByAccount(sellerId).map(profile -> profile.validatedAt() != null)
                    .orElse(false);
            if (!validated) return;
            invoices.enqueue(UUID.randomUUID(), chargeId, sellerId);
        } catch (RuntimeException exception) {
            log.warn("Falha ao enfileirar nota fiscal para a cobrança {} (não bloqueia o pagamento)", chargeId,
                    exception);
        }
    }
}
