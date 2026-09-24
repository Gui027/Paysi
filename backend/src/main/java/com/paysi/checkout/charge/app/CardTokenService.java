package com.paysi.checkout.charge.app;

import com.paysi.checkout.charge.port.CardTokenOrderLookup;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import com.paysi.payment.provider.CardTokenRequest;
import com.paysi.payment.provider.CardTokenResult;
import com.paysi.payment.provider.CardTokenizationException;
import com.paysi.payment.provider.CardTokenizer;
import com.paysi.security.ratelimit.app.CheckoutRateLimitGuard;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Troca os dados do cartão por um token do provedor para um pedido em cartão. O comprador
 * vem sempre do pedido (nunca do corpo) — o token nasce vinculado ao dono do pedido. Os dados
 * do cartão só existem dentro desta chamada: não são gravados nem logados.
 */
@Service
public class CardTokenService {
    private final CardTokenOrderLookup orders;
    private final CardTokenizer tokenizer;
    private final CheckoutRateLimitGuard rateLimit;

    public CardTokenService(CardTokenOrderLookup orders, CardTokenizer tokenizer, CheckoutRateLimitGuard rateLimit) {
        this.orders = orders;
        this.tokenizer = tokenizer;
        this.rateLimit = rateLimit;
    }

    public CardTokenResult tokenize(UUID orderId, CardData card, String remoteIp) {
        rateLimit.checkCardTokenAttempt(remoteIp, orderId);
        var buyer = orders.findBuyerOfPendingCardOrder(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Pedido não encontrado"));
        try {
            return tokenizer.tokenize(new CardTokenRequest(buyer, card.holderName(), card.number(),
                    card.expiryMonth(), card.expiryYear(), card.ccv(), card.postalCode(), card.addressNumber(),
                    card.phone(), remoteIp));
        } catch (CardTokenizationException error) {
            throw new ValidationException("CARD_TOKENIZATION_FAILED", error.getMessage(), "card");
        }
    }

    public record CardData(String holderName, String number, String expiryMonth, String expiryYear, String ccv,
                           String postalCode, String addressNumber, String phone) {
        @Override
        public String toString() {
            return "CardData[REDACTED]";
        }
    }
}
