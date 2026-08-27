package com.paysi.identity.kyc.webhook.app;
public record KycWebhookResult(String providerEventId,boolean idempotentReplay) { }
