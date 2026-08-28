package com.paysi.payment.inbox.domain;

public record ProviderEventResult(String status, boolean idempotentReplay) {}
