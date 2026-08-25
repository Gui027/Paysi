package com.paysi.core.money;

/** Percentual em pontos-base: 10.000 bps = 100%. */
public record Bps(int value) {
    public static final int WHOLE = 10_000;

    public Bps {
        if (value < 0 || value > WHOLE) {
            throw new IllegalArgumentException("bps deve estar entre 0 e 10.000");
        }
    }

    public long applyTo(long cents) {
        return Math.multiplyExact(cents, (long) value) / WHOLE;
    }
}

