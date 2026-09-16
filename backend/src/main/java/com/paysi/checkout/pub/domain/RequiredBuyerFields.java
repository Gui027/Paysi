package com.paysi.checkout.pub.domain;

import com.paysi.catalog.product.domain.Segment;
import com.paysi.identity.domain.PersonType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fonte única dos campos que o comprador precisa informar. É a oferta que decide,
 * não o vendedor (ADR-13), e a mesma regra vale nas duas pontas: o contrato público
 * anuncia a lista e a criação do pedido a cobra.
 *
 * <p>Telefone não está aqui de propósito: RF-022 só admite nome, e-mail e documento
 * como obrigatórios, salvo justificativa operacional.
 */
public final class RequiredBuyerFields {
    public static final List<String> BASE = List.of("name", "email", "personType", "taxId");

    /** RF-093: dados fiscais e endereço completo para SaaS ou comprador PJ. */
    public static final List<String> COMPANY = List.of("legalName", "municipalReg",
            "address.zipCode", "address.street", "address.number", "address.complement",
            "address.district", "address.city", "address.state");

    private RequiredBuyerFields() { }

    public static Map<PersonType, List<String>> byPersonType(Segment segment) {
        return Map.of(
                PersonType.PF, requiresCompanyData(segment, PersonType.PF) ? merged() : BASE,
                PersonType.PJ, merged());
    }

    public static boolean requiresCompanyData(Segment segment, PersonType personType) {
        return segment == Segment.SAAS || personType == PersonType.PJ;
    }

    private static List<String> merged() {
        List<String> merged = new ArrayList<>(BASE);
        merged.addAll(COMPANY);
        return List.copyOf(merged);
    }
}
