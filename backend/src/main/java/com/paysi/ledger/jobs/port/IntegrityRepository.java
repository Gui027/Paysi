package com.paysi.ledger.jobs.port;

public interface IntegrityRepository {
    long violations(String view);
}
