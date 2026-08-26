package com.paysi.identity.domain;

import com.paysi.core.error.ValidationException;

/**
 * CPF (PF) ou CNPJ (PJ) normalizado e validado por dígito verificador.
 * RF-005: pessoa física e jurídica têm documentos de formatos distintos.
 */
public record TaxId(String digits) {

    public TaxId {
        if (digits == null || digits.isBlank()) {
            throw new ValidationException("REQUIRED", "Documento é obrigatório", "taxId");
        }
    }

    /** Remove formatação e confere o dígito verificador para o tipo de pessoa informado. */
    public static TaxId of(String raw, PersonType personType) {
        String normalized = raw == null ? "" : raw.replaceAll("\\D", "");
        boolean valid = switch (personType) {
            case PF -> normalized.length() == 11 && isValidCpf(normalized);
            case PJ -> normalized.length() == 14 && isValidCnpj(normalized);
        };
        if (!valid) {
            throw new ValidationException("INVALID_TAX_ID", "Documento inválido para o tipo de pessoa informado", "taxId");
        }
        return new TaxId(normalized);
    }

    private static boolean isValidCpf(String cpf) {
        if (allSameDigit(cpf)) {
            return false;
        }
        int firstCheck = cpfCheckDigit(cpf, 9, 10);
        if (firstCheck != digitAt(cpf, 9)) {
            return false;
        }
        int secondCheck = cpfCheckDigit(cpf, 10, 11);
        return secondCheck == digitAt(cpf, 10);
    }

    private static int cpfCheckDigit(String cpf, int length, int firstWeight) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(cpf, i) * (firstWeight - i);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean isValidCnpj(String cnpj) {
        if (allSameDigit(cnpj)) {
            return false;
        }
        int[] firstWeights = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int firstCheck = cnpjCheckDigit(cnpj, 12, firstWeights);
        if (firstCheck != digitAt(cnpj, 12)) {
            return false;
        }
        int[] secondWeights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int secondCheck = cnpjCheckDigit(cnpj, 13, secondWeights);
        return secondCheck == digitAt(cnpj, 13);
    }

    private static int cnpjCheckDigit(String cnpj, int length, int[] weights) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digitAt(cnpj, i) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static boolean allSameDigit(String digits) {
        char first = digits.charAt(0);
        return digits.chars().allMatch(c -> c == first);
    }

    private static int digitAt(String digits, int index) {
        return digits.charAt(index) - '0';
    }
}
