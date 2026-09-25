package com.paysi.catalog.offer.domain;

import com.paysi.checkout.order.app.CreateOrderCommand;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfferValuesReturnUrlTest {
    private static OfferValues withReturnUrl(String url) {
        return new OfferValues(2_000, null, 0, true, 7, 1, 3, 5, Set.of(OfferPaymentMethod.PIX),
                OfferPayoutDelay.D32, null, url);
    }

    @Test
    void acceptsHttpsAndLocalhostAndTreatsBlankAsAbsent() {
        assertThat(withReturnUrl(" https://app.exemplo.com/obrigado ").returnUrl())
                .isEqualTo("https://app.exemplo.com/obrigado");
        assertThat(withReturnUrl("http://localhost:3000/ok").returnUrl()).isEqualTo("http://localhost:3000/ok");
        assertThat(withReturnUrl("   ").returnUrl()).isNull();
        assertThat(withReturnUrl(null).returnUrl()).isNull();
    }

    @Test
    void rejectsUnsafeOrMalformedUrls() {
        for (String bad : new String[]{"http://exemplo.com/ok", "javascript:alert(1)", "https://user:senha@exemplo.com",
                "ftp://exemplo.com", "https://", "não é url", "https://exemplo.com/" + "a".repeat(500)}) {
            assertThatThrownBy(() -> withReturnUrl(bad)).as(bad).isInstanceOf(ValidationException.class);
        }
    }

    @Test
    void orderReferenceIsTrimmedBlankBecomesNullAndLongOnesAreRejected() {
        assertThat(command("  user_123 ").reference()).isEqualTo("user_123");
        assertThat(command("   ").reference()).isNull();
        assertThatThrownBy(() -> command("x".repeat(129))).isInstanceOf(ValidationException.class);
    }

    private static CreateOrderCommand command(String reference) {
        return new CreateOrderCommand("Maria", "maria@exemplo.com", com.paysi.identity.domain.PersonType.PF,
                "52998224725", null, null, null, OfferPaymentMethod.PIX, 1, null, null, null,
                "termos-hash-123", reference);
    }
}
