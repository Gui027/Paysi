package com.paysi.ledger.jobs.app;

public record IntegrityViolation(String view, long rows) { }
