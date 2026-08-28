package com.paysi.payment.card.domain;

public record CardPaymentCommand(String cardToken, int installments, SaleEvidenceCommand evidence) {
    public CardPaymentCommand {
        if (cardToken == null || cardToken.isBlank()) throw new IllegalArgumentException("Token é obrigatório");
        if (installments < 1 || installments > 12) throw new IllegalArgumentException("Parcelas inválidas");
        if (evidence == null) throw new IllegalArgumentException("Evidência da venda é obrigatória");
    }

    @Override
    public String toString() {
        return "CardPaymentCommand[cardToken=[REDACTED], installments=" + installments
                + ", evidence=[REDACTED]]";
    }
}
