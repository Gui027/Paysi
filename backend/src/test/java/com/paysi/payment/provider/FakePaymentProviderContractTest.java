package com.paysi.payment.provider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class FakePaymentProviderContractTest extends PaymentProviderContractTest {
    @Override
    protected PaymentProvider provider(FakeProviderOutcome outcome) {
        return new FakePaymentProvider(outcome,
                Clock.fixed(Instant.parse("2026-08-28T12:00:00Z"), ZoneOffset.UTC));
    }
}
