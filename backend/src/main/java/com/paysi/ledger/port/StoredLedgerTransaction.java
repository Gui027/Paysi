package com.paysi.ledger.port;
import java.util.UUID;
public record StoredLedgerTransaction(UUID id, String commandHash) { }
