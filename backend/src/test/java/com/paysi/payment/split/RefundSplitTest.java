package com.paysi.payment.split;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefundSplitTest {
    @Test
    void usesCumulativeTruncationForPartialRefund() {
        Split original = SplitEngine.split(10_000, PaymentMethod.CARD_1, Plan.TRANSACIONAL, 1_000);

        RefundPart firstSlice = RefundSplit.slice(original, 10_000, 0, 2_000);

        assertThat(firstSlice).isEqualTo(new RefundPart(1_641, 200, 90, 69));
        assertThat(firstSlice.totalCents()).isEqualTo(2_000);
    }

    @Test
    void completeSequenceReturnsExactlyTheOriginalAllocation() {
        Split original = SplitEngine.split(10_000, PaymentMethod.CARD_1, Plan.TRANSACIONAL, 1_000);
        long seller = 0, affiliate = 0, platform = 0, provider = 0, refunded = 0;

        for (long slice : InstallmentSplit.byInstallment(10_000, 11)) {
            RefundPart part = RefundSplit.slice(original, 10_000, refunded, slice);
            refunded += slice;
            seller += part.sellerCents();
            affiliate += part.affiliateCents();
            platform += part.platformCents();
            provider += part.providerCents();
        }

        assertThat(new Split(seller, affiliate, platform, provider, original.sellerFeeCents()))
                .isEqualTo(original);
    }
}

