package com.paysi.payment.inbox.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProviderEventRetryJob {
    private final ProviderEventService service;

    public ProviderEventRetryJob(ProviderEventService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${paysi.payment.inbox-retry-ms:60000}")
    public void retry() {
        service.retryFailed(100);
    }
}
