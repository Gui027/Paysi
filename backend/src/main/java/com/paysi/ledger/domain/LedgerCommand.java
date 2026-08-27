package com.paysi.ledger.domain;

import java.util.List;

public record LedgerCommand(TransactionType type, LedgerReference reference, String description,
                            List<LedgerEntry> entries) {
    public LedgerCommand {
        if (type == null || reference == null) throw new IllegalArgumentException("Ledger type and reference are required");
        if (description == null || description.isBlank() || description.length() > 500) throw new IllegalArgumentException("Ledger description is invalid");
        if (entries == null || entries.isEmpty()) throw new IllegalArgumentException("Ledger entries are required");
        entries = List.copyOf(entries);
        long debits = sum(entries, Direction.DEBIT);
        long credits = sum(entries, Direction.CREDIT);
        if (debits != credits) throw new IllegalArgumentException("Ledger transaction must sum to zero");
    }

    public String naturalKey() { return type.name() + ":" + reference.type().name() + ":" + reference.id(); }

    private static long sum(List<LedgerEntry> entries, Direction direction) {
        long total = 0;
        for (var entry : entries) if (entry.direction() == direction) total = Math.addExact(total, entry.amountCents());
        return total;
    }
}
