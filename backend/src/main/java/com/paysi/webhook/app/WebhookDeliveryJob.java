package com.paysi.webhook.app;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookDeliveryJob {
    private final WebhookDeliveryService deliveries;
    public WebhookDeliveryJob(WebhookDeliveryService deliveries) { this.deliveries = deliveries; }

    @Scheduled(fixedDelayString = "${paysi.webhook.delivery-ms:10000}")
    public void publish() { deliveries.publish(100); }

    @Scheduled(fixedDelayString = "${paysi.webhook.retry-ms:60000}")
    public void retry() { deliveries.retry(100); }
}
