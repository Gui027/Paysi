package com.paysi.fiscal.app;

import com.paysi.fiscal.port.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * RF-112: chamado por {@code RefundService} depois que um reembolso (total ou parcial) foi
 * aplicado com sucesso. Apenas marca a nota já emitida como CANCEL_REQUESTED — quem chama
 * o emissor é o processador assíncrono ({@link InvoiceIssuingService}), pelo mesmo motivo de
 * RF-113: a confirmação do reembolso não pode ficar refém da prefeitura. Se não existe nota
 * emitida para a cobrança (ainda na fila, já falhou, ou o vendedor não emite notas), não há
 * o que cancelar — no-op silencioso.
 */
@Service
public class InvoiceCancellationService {
    private static final Logger log = LoggerFactory.getLogger(InvoiceCancellationService.class);

    private final InvoiceRepository invoices;

    public InvoiceCancellationService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public void requestCancellationForRefund(UUID chargeId) {
        try {
            invoices.requestCancellation(chargeId);
        } catch (RuntimeException exception) {
            log.warn("Falha ao solicitar cancelamento de nota fiscal para a cobrança {} (não bloqueia o reembolso)",
                    chargeId, exception);
        }
    }
}
