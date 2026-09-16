package com.paysi.checkout.order.domain;

import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pub.domain.RequiredBuyerFields;
import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Comprador do checkout. É o registro vivo; o retrato usado na venda vive em
 * {@code orders.buyer_snapshot} (documento 2, §3.4).
 *
 * <p>O {@code toString} é mascarado de propósito: documento e e-mail nunca aparecem
 * completos em log ou rastreamento (documento 3, §4).
 */
public record Buyer(
        UUID id,
        String name,
        String email,
        PersonType personType,
        String taxId,
        String legalName,
        String municipalReg,
        BuyerAddress address
) {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public Buyer {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        legalName = blankToNull(legalName);
        municipalReg = blankToNull(municipalReg);
    }

    /**
     * Valida o comprador contra o segmento da oferta: é a oferta que decide quais
     * campos são obrigatórios, não o formulário.
     */
    public static Buyer create(UUID id, String name, String email, PersonType personType,
            String rawTaxId, String legalName, String municipalReg, BuyerAddress address,
            Segment segment) {
        require(filled(name), "name", "Informe o nome do comprador");
        require(email != null && EMAIL.matcher(email.trim()).matches(), "email",
                "Informe um e-mail válido");
        if (personType == null) {
            throw new ValidationException("BUYER_INVALID", "Informe o tipo de pessoa", "personType");
        }
        TaxId taxId = TaxId.of(rawTaxId, personType);

        Buyer buyer = new Buyer(id, name, email, personType, taxId.digits(), legalName,
                municipalReg, address);
        if (RequiredBuyerFields.requiresCompanyData(segment, personType)) {
            require(filled(buyer.legalName()), "legalName", "Informe a razão social");
            require(buyer.address() != null, "address", "Informe o endereço completo");
            buyer.address().requireComplete();
        }
        return buyer;
    }

    @Override
    public String toString() {
        return "Buyer[id=" + id + ", personType=" + personType
                + ", name=[REDACTED], email=[REDACTED], taxId=[REDACTED], address=[REDACTED]]";
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void require(boolean condition, String field, String message) {
        if (!condition) throw new ValidationException("BUYER_INVALID", message, field);
    }
}
