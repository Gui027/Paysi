package com.paysi.checkout.order.domain;

import com.paysi.identity.domain.PersonType;

import java.time.Instant;

/**
 * Retrato imutável do comprador no momento da venda, gravado em
 * {@code orders.buyer_snapshot}. Serve de prova em contestação, e é por isso que ele
 * não acompanha edições posteriores do registro vivo em {@code buyers}.
 *
 * <p>Carrega também o aceite (RF-026). A evidência completa da venda vive em
 * {@code sale_evidence}, cuja chave é a cobrança — que só passa a existir no BE-07.1.
 */
public record BuyerSnapshot(
        String name,
        String email,
        PersonType personType,
        String taxId,
        String legalName,
        String municipalReg,
        BuyerAddress address,
        String termsHash,
        Instant termsAcceptedAt
) {
    public static BuyerSnapshot of(Buyer buyer, String termsHash, Instant acceptedAt) {
        return new BuyerSnapshot(buyer.name(), buyer.email(), buyer.personType(), buyer.taxId(),
                buyer.legalName(), buyer.municipalReg(), buyer.address(), termsHash, acceptedAt);
    }

    @Override
    public String toString() {
        return "BuyerSnapshot[personType=" + personType + ", termsHash=" + termsHash
                + ", termsAcceptedAt=" + termsAcceptedAt + ", buyer=[REDACTED]]";
    }
}
