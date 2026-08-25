package com.paysi.core.money;

import java.util.Objects;

/** Valor monetário inteiro em centavos. Nunca representa ponto flutuante. */
public record Money(long cents) implements Comparable<Money> {
    public static final Money ZERO = new Money(0);

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public Money add(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money subtract(Money other) {
        Objects.requireNonNull(other, "other");
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money apply(Bps rate) {
        Objects.requireNonNull(rate, "rate");
        return new Money(rate.applyTo(cents));
    }

    public boolean isNegative() {
        return cents < 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }
}

