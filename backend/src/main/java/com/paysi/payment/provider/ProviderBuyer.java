package com.paysi.payment.provider;

public record ProviderBuyer(String name, String email, String personType, String taxId) {
    public ProviderBuyer {
        if (blank(name) || blank(email) || blank(personType) || blank(taxId)) {
            throw new IllegalArgumentException("Dados normalizados do comprador são obrigatórios");
        }
        if (!java.util.Set.of("PF", "PJ").contains(personType)) {
            throw new IllegalArgumentException("Tipo de pessoa inválido");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
