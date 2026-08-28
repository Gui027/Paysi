package com.paysi.payout.app;
import java.util.UUID;
public record PayoutResult(UUID payoutId,String status,String receiptUrl,boolean idempotentReplay) { }
