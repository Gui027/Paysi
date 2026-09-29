package com.paysi.payment.provider;

/**
 * {@code sellerWalletId}/{@code affiliateWalletId}: identificador da subconta do provedor (hoje só a Asaas
 * usa isso) para onde a fatia respectiva deve ser roteada de verdade. Nulo quando a conta ainda não tem
 * subconta — nesse caso o provedor não recebe instrução de split nenhuma para aquela fatia e o valor cheio
 * fica na conta mestre, exatamente como antes desta divisão existir (RF de compatibilidade: nenhum vendedor
 * fica bloqueado de vender por ainda não ter subconta).
 */
public record ProviderSplit(long sellerCents, long affiliateCents, long platformCents,
                            String sellerWalletId, String affiliateWalletId) {
    public ProviderSplit(long sellerCents, long affiliateCents, long platformCents) {
        this(sellerCents, affiliateCents, platformCents, null, null);
    }

    public ProviderSplit {
        if (sellerCents < 0 || affiliateCents < 0 || platformCents < 0) {
            throw new IllegalArgumentException("Split não aceita valores negativos");
        }
    }

    public long totalCents() {
        return Math.addExact(Math.addExact(sellerCents, affiliateCents), platformCents);
    }
}
