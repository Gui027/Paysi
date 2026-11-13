package com.paysi.checkout.pub.domain;

import com.paysi.identity.domain.PersonType;

import java.util.List;
import java.util.Map;

/**
 * Fonte única dos campos que o comprador precisa informar. É a oferta que decide,
 * não o vendedor (ADR-13), e a mesma regra vale nas duas pontas: o contrato público
 * anuncia a lista e a criação do pedido a cobra.
 *
 * <p>O checkout público segue o cadastro mínimo: nome, e-mail, CPF/CNPJ e celular.
 * O tipo de pessoa é inferido pelo documento no navegador, mas continua no contrato
 * técnico enviado ao backend.
 */
public final class RequiredBuyerFields {
    public static final List<String> BASE = List.of("name", "email", "personType", "taxId", "phone");

    private RequiredBuyerFields() { }

    public static Map<PersonType, List<String>> byPersonType() {
        return Map.of(PersonType.PF, BASE, PersonType.PJ, BASE);
    }
}
