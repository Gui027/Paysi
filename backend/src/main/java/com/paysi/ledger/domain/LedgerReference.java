package com.paysi.ledger.domain;

public record LedgerReference(ReferenceType type, String id) {
    public LedgerReference {
        if (type == null) throw new IllegalArgumentException("Reference type is required");
        if (id == null || id.isBlank() || id.length() > 200) throw new IllegalArgumentException("Reference id is invalid");
    }
}
