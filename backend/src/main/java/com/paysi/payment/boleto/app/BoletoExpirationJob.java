package com.paysi.payment.boleto.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BoletoExpirationJob {
    private final BoletoPaymentService service;

    public BoletoExpirationJob(BoletoPaymentService service) {
        this.service = service;
    }

    @Scheduled(cron = "${paysi.payment.boleto-expiration-cron:0 */5 * * * *}")
    public void expireDue() {
        service.expireDue();
    }
}
