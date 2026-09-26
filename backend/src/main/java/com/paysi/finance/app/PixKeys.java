package com.paysi.finance.app;

import com.paysi.core.error.ValidationException;
import com.paysi.identity.domain.PersonType;
import com.paysi.identity.domain.TaxId;

import java.util.regex.Pattern;

/** Reconhece o tipo da chave Pix digitada e a normaliza (o vendedor só cola a chave, sem escolher o tipo). */
public final class PixKeys {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern EVP = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private PixKeys() { }

    public record Detected(String type, String key) { }

    public static Detected detect(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty() || text.length() > 140) throw invalid();
        if (text.contains("@")) {
            if (!EMAIL.matcher(text).matches()) throw invalid();
            return new Detected("EMAIL", text.toLowerCase());
        }
        if (EVP.matcher(text).matches()) return new Detected("EVP", text.toLowerCase());
        String digits = text.replaceAll("\\D", "");
        if (text.startsWith("+")) return phone(digits);
        if (digits.length() == 14 && isValid(digits, PersonType.PJ)) return new Detected("CNPJ", digits);
        if (digits.length() == 11 && isValid(digits, PersonType.PF)) return new Detected("CPF", digits);
        if (digits.length() == 10 || digits.length() == 11) return phone(digits);
        throw invalid();
    }

    private static Detected phone(String digits) {
        String national = digits.startsWith("55") && digits.length() >= 12 ? digits.substring(2) : digits;
        if (national.length() != 10 && national.length() != 11) throw invalid();
        return new Detected("PHONE", "+55" + national);
    }

    private static boolean isValid(String digits, PersonType type) {
        try {
            TaxId.of(digits, type);
            return true;
        } catch (ValidationException error) {
            return false;
        }
    }

    private static ValidationException invalid() {
        return new ValidationException("PIX_KEY_INVALID",
                "Informe uma chave Pix válida: CPF, CNPJ, e-mail, celular ou chave aleatória", "pixKey");
    }
}
