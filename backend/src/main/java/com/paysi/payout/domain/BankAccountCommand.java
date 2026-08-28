package com.paysi.payout.domain;

public record BankAccountCommand(String holderType, String holderTaxId, String holderName, String bankCode,
                                 String branch, String accountNumber, String digit, String accountType,
                                 String pixKeyType, String pixKey) {
    public BankAccountCommand {
        if (blank(holderType) || blank(holderTaxId) || blank(holderName) || blank(bankCode) || blank(branch)
                || blank(accountNumber) || blank(digit) || blank(accountType) || blank(pixKeyType) || blank(pixKey))
            throw new IllegalArgumentException("Todos os dados bancários são obrigatórios");
        if (!java.util.Set.of("PF", "PJ").contains(holderType))
            throw new IllegalArgumentException("Tipo de titular inválido");
        if (!java.util.Set.of("CHECKING", "SAVINGS", "PAYMENT").contains(accountType))
            throw new IllegalArgumentException("Tipo de conta inválido");
        if (!java.util.Set.of("CPF", "CNPJ", "EMAIL", "PHONE", "EVP").contains(pixKeyType))
            throw new IllegalArgumentException("Tipo de chave Pix inválido");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
