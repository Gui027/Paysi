package com.paysi.payment.split;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitEngineTest {
    @Test
    void reproducesDocumentedCardExample() {
        Split result = SplitEngine.split(10_000, PaymentMethod.CARD_1, Plan.TRANSACIONAL, 1_000);

        assertThat(result).isEqualTo(new Split(8_201, 1_000, 451, 348, 799));
        assertThat(result.allocatedCents()).isEqualTo(10_000);
    }

    @Test
    void rejectsCommissionAboveCommercialLimit() {
        assertThatThrownBy(() -> SplitEngine.split(10_000, PaymentMethod.PIX, Plan.TRANSACIONAL, 5_001))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

