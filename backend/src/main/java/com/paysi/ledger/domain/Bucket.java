package com.paysi.ledger.domain;

public enum Bucket {
    GUARANTEE, PENDING, RESERVE, AVAILABLE, DEBT, SYSTEM;
    public boolean userBucket() { return this != SYSTEM; }
}
