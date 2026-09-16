package com.paysi.checkout.order.domain;

import com.paysi.core.error.ValidationException;

/** Endereço do comprador. Exigido por RF-093 em SaaS ou quando o comprador é PJ. */
public record BuyerAddress(
        String zipCode,
        String street,
        String number,
        String complement,
        String district,
        String city,
        String state
) {
    public BuyerAddress {
        zipCode = zipCode == null ? null : zipCode.replaceAll("\\D", "");
        state = state == null ? null : state.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Conjunto mínimo para emitir nota fiscal: CEP, logradouro, número, cidade e UF. */
    void requireComplete() {
        require(zipCode != null && zipCode.length() == 8, "address.zipCode", "Informe um CEP válido");
        require(filled(street), "address.street", "Informe o logradouro");
        require(filled(number), "address.number", "Informe o número");
        require(filled(city), "address.city", "Informe a cidade");
        require(state != null && state.length() == 2, "address.state", "Informe a UF com duas letras");
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String field, String message) {
        if (!condition) throw new ValidationException("BUYER_INVALID", message, field);
    }
}
