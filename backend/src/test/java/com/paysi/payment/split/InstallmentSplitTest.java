package com.paysi.payment.split;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentSplitTest {
    @Test
    void usesLargestRemainderAndKeepsExactSum() {
        var result = InstallmentSplit.byInstallment(8_201, 12);

        assertThat(result).containsExactly(684L, 684L, 684L, 684L, 684L,
                683L, 683L, 683L, 683L, 683L, 683L, 683L);
        assertThat(result).hasSize(12);
        assertThat(result.stream().mapToLong(Long::longValue).sum()).isEqualTo(8_201);
    }
}

