package com.paysi.checkout.charge.app;

import com.paysi.checkout.charge.port.CardTokenOrderLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.TooManyRequestsException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.provider.*;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CardTokenServiceTest {
    private static final UUID ORDER = UUID.randomUUID();
    private static final ProviderBuyer BUYER = new ProviderBuyer("Buyer", "b@example.com", "PF", "52998224725");
    private static final CardTokenService.CardData CARD = new CardTokenService.CardData(
            "BUYER", "4111111111111111", "12", "2030", "123", "29700000", "10", "27999999999");

    private final CardTokenOrderLookup orders = mock(CardTokenOrderLookup.class);
    private final CardTokenizer tokenizer = mock(CardTokenizer.class);
    private final CheckoutRateLimitGuard rateLimit = mock(CheckoutRateLimitGuard.class);
    private final CardTokenService service = new CardTokenService(orders, tokenizer, rateLimit);

    @Test
    void tokenizesUsingTheBuyerOfTheOrderNotAnythingFromTheBody() {
        when(orders.findBuyerOfPendingCardOrder(ORDER)).thenReturn(Optional.of(BUYER));
        when(tokenizer.tokenize(any())).thenReturn(new CardTokenResult("tok_1", "VISA", "1111"));

        var result = service.tokenize(ORDER, CARD, "1.2.3.4");

        assertThat(result.token()).isEqualTo("tok_1");
        verify(rateLimit).checkCardTokenAttempt("1.2.3.4", ORDER);
        verify(tokenizer).tokenize(argThat(request -> request.buyer().equals(BUYER)
                && request.remoteIp().equals("1.2.3.4") && request.number().equals("4111111111111111")));
    }

    @Test
    void unknownOrNonCardOrderIsNotFoundAndNeverReachesTheProvider() {
        when(orders.findBuyerOfPendingCardOrder(ORDER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.tokenize(ORDER, CARD, "1.2.3.4")).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(tokenizer);
    }

    @Test
    void providerRefusalBecomesAControlledValidationError() {
        when(orders.findBuyerOfPendingCardOrder(ORDER)).thenReturn(Optional.of(BUYER));
        when(tokenizer.tokenize(any())).thenThrow(new CardTokenizationException("invalid_creditCard", "Cartão recusado", false));

        assertThatThrownBy(() -> service.tokenize(ORDER, CARD, "1.2.3.4"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Cartão recusado");
    }

    @Test
    void rateLimitBlocksBeforeAnyLookupOrProviderCall() {
        doThrow(new TooManyRequestsException("RATE_LIMITED", "muitas", null))
                .when(rateLimit).checkCardTokenAttempt(any(), any());

        assertThatThrownBy(() -> service.tokenize(ORDER, CARD, "1.2.3.4")).isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(orders, tokenizer);
    }

    @Test
    void cardDataNeverAppearsInToString() {
        assertThat(CARD.toString()).doesNotContain("4111", "123");
        assertThat(new CardTokenRequest(BUYER, "B", "4111111111111111", "12", "2030", "123", "1", "1", "1", "ip")
                .toString()).doesNotContain("4111", "b@example.com");
    }
}
