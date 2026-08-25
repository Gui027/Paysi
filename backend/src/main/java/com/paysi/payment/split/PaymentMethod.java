package com.paysi.payment.split;

public enum PaymentMethod {
    PIX(0, 199, 399, 199),
    BOLETO(0, 199, 399, 199),
    CARD_1(299, 49, 599, 399),
    CARD_6(349, 49, 649, 449),
    CARD_12(399, 49, 699, 499);

    private final int providerBps;
    private final long providerFixedCents;
    private final int transactionalFeeBps;
    private final int scaleFeeBps;

    PaymentMethod(int providerBps, long providerFixedCents, int transactionalFeeBps, int scaleFeeBps) {
        this.providerBps = providerBps;
        this.providerFixedCents = providerFixedCents;
        this.transactionalFeeBps = transactionalFeeBps;
        this.scaleFeeBps = scaleFeeBps;
    }

    public int providerBps() { return providerBps; }
    public long providerFixedCents() { return providerFixedCents; }
    public int feeBps(Plan plan) {
        return plan == Plan.ESCALA ? scaleFeeBps : transactionalFeeBps;
    }
}

