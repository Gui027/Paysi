package com.paysi.identity.kyc.webhook.port;
public interface WebhookSignatureVerifier { boolean valid(String payload,String signature); }
