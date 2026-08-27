package com.paysi.ledger.app;
import java.util.UUID;
public record LedgerWriteResult(UUID transactionId, boolean idempotentReplay) { }
