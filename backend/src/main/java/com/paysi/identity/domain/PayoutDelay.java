package com.paysi.identity.domain;

/** RF-067: prazo de recebimento escolhido pela conta. Toda conta nasce em {@link #D32}. */
public enum PayoutDelay {
    D32,
    D15,
    D7,
    D2
}
