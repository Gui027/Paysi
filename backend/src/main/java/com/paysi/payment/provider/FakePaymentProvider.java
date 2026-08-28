package com.paysi.payment.provider;

import com.paysi.core.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "paysi.provider", havingValue = "fake")
public class FakePaymentProvider implements PaymentProvider {
    private final FakeProviderOutcome outcome;
    private final Clock clock;
    private final Map<UUID, StoredCall> calls = new ConcurrentHashMap<>();

    public FakePaymentProvider(@Value("${paysi.payment.fake-outcome:APPROVED}") String outcome) {
        this(FakeProviderOutcome.valueOf(outcome.toUpperCase()), Clock.systemUTC());
    }

    FakePaymentProvider(FakeProviderOutcome outcome, Clock clock) {
        this.outcome = outcome;
        this.clock = clock;
    }

    @Override
    public ProviderPaymentResult charge(ProviderPaymentRequest request) {
        var stored = calls.compute(request.orderId(), (id, existing) -> {
            if (existing != null && !existing.request().equals(request)) {
                throw new ConflictException("PROVIDER_ORDER_REUSED",
                        "Pedido já enviado ao provedor com outro conteúdo", "orderId");
            }
            return existing == null ? new StoredCall(request, create(request)) : existing;
        });
        return stored.result();
    }

    @Override
    public ProviderPaymentResult confirmThreeDs(ProviderThreeDsConfirmation confirmation) {
        var stored = calls.get(confirmation.orderId());
        if (stored == null || !stored.result().providerChargeId().equals(confirmation.providerChargeId())
                || stored.result().status() != ProviderChargeStatus.PENDING) {
            return invalidChallenge(confirmation.providerChargeId(), 0);
        }
        if (!confirmation.challengeToken().equals("fake-3ds-valid")) {
            return invalidChallenge(confirmation.providerChargeId(), stored.result().providerFeeCents());
        }
        var approved = new ProviderPaymentResult(stored.result().providerChargeId(),
                ProviderChargeStatus.APPROVED, null, stored.result().providerFeeCents(),
                stored.result().receivables(), new ProviderThreeDs("AUTHENTICATED", null, "05"),
                null, false);
        calls.put(confirmation.orderId(), new StoredCall(stored.request(), approved));
        return approved;
    }

    private ProviderPaymentResult create(ProviderPaymentRequest request) {
        String chargeId = "fake_charge_" + request.orderId();
        Instant now = clock.instant();
        long fee = Math.max(1, request.amountCents() * 2 / 100);
        var status = request.method() == ProviderPaymentMethod.BOLETO
                && outcome == FakeProviderOutcome.APPROVED
                ? ProviderChargeStatus.PENDING : mappedStatus();
        var data = paymentData(request, status, now);
        var threeDs = threeDs(request, status);
        String error = outcome == FakeProviderOutcome.TIMEOUT ? "PROVIDER_TIMEOUT"
                : outcome == FakeProviderOutcome.ERROR ? "PROVIDER_ERROR" : null;
        var receivables = status == ProviderChargeStatus.APPROVED || status == ProviderChargeStatus.PENDING
                ? receivables(request, now) : java.util.List.<ProviderReceivable>of();
        return new ProviderPaymentResult(chargeId, status, data, fee, receivables, threeDs, error,
                outcome == FakeProviderOutcome.TIMEOUT || outcome == FakeProviderOutcome.ERROR);
    }

    private ProviderChargeStatus mappedStatus() {
        return switch (outcome) {
            case APPROVED -> ProviderChargeStatus.APPROVED;
            case DECLINED -> ProviderChargeStatus.DECLINED;
            case PENDING -> ProviderChargeStatus.PENDING;
            case EXPIRED -> ProviderChargeStatus.EXPIRED;
            case ERROR, TIMEOUT -> ProviderChargeStatus.ERROR;
        };
    }

    private static ProviderPaymentData paymentData(ProviderPaymentRequest request,
                                                     ProviderChargeStatus status, Instant now) {
        if (status != ProviderChargeStatus.PENDING && status != ProviderChargeStatus.APPROVED) return null;
        return switch (request.method()) {
            case PIX -> new ProviderPaymentData("000201FAKE" + request.orderId(), null, null,
                    now.plus(30, ChronoUnit.MINUTES));
            case BOLETO -> new ProviderPaymentData(null, "34191FAKE" + request.orderId(),
                    "https://fake.paysi/boleto/" + request.orderId(),
                    now.plus(request.boletoDueDays(), ChronoUnit.DAYS));
            case CARD -> null;
        };
    }

    private static ProviderThreeDs threeDs(ProviderPaymentRequest request, ProviderChargeStatus status) {
        if (request.method() != ProviderPaymentMethod.CARD) {
            return new ProviderThreeDs("NOT_APPLICABLE", null, null);
        }
        return switch (status) {
            case APPROVED -> new ProviderThreeDs("AUTHENTICATED", null, "05");
            case PENDING -> new ProviderThreeDs("CHALLENGE_REQUIRED",
                    "https://fake.paysi/3ds/" + request.orderId(), null);
            case DECLINED -> new ProviderThreeDs("FAILED", null, null);
            case EXPIRED, ERROR -> new ProviderThreeDs("NOT_APPLICABLE", null, null);
        };
    }

    private static ProviderPaymentResult invalidChallenge(String providerChargeId, long providerFeeCents) {
        return new ProviderPaymentResult(providerChargeId, ProviderChargeStatus.DECLINED,
                null, providerFeeCents, java.util.List.of(), new ProviderThreeDs("FAILED", null, null),
                "THREE_DS_INVALID", false);
    }

    private static java.util.List<ProviderReceivable> receivables(ProviderPaymentRequest request, Instant now) {
        var result = new ArrayList<ProviderReceivable>();
        long base = request.amountCents() / request.installments();
        long remainder = request.amountCents() % request.installments();
        for (int sequence = 1; sequence <= request.installments(); sequence++) {
            long amount = base + (sequence <= remainder ? 1 : 0);
            result.add(new ProviderReceivable(sequence,
                    "fake_receivable_" + request.orderId() + "_" + sequence,
                    now.plus(sequence * 30L, ChronoUnit.DAYS), amount));
        }
        return result;
    }

    private record StoredCall(ProviderPaymentRequest request, ProviderPaymentResult result) {}
}
