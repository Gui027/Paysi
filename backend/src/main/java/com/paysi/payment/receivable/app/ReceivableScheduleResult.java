package com.paysi.payment.receivable.app;

public record ReceivableScheduleResult(int receivables, boolean idempotentReplay) { }
