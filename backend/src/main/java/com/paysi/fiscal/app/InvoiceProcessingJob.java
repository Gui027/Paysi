package com.paysi.fiscal.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fila fiscal (docs/02 §4.6): emite e cancela notas, com retentativa exponencial, a cada 5 min por padrão. */
@Component
public class InvoiceProcessingJob {
    private final InvoiceIssuingService service;

    public InvoiceProcessingJob(InvoiceIssuingService service) {
        this.service = service;
    }

    @Scheduled(cron = "${paysi.fiscal.invoice-processing-cron:0 */5 * * * *}")
    public void process() {
        service.processBatch(50);
    }
}
