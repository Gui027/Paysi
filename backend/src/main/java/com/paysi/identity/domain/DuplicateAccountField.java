package com.paysi.identity.domain;

import com.paysi.core.error.ConflictException;

import java.util.Arrays;
import java.util.Optional;

/**
 * RF-117 / [FIX-D02]: os dois campos com unicidade condicional a contas não encerradas
 * (V001, {@code uq_accounts_email_open} e {@code uq_accounts_tax_id_open}). Centraliza
 * código, mensagem e nome do índice para que a verificação prévia do serviço e a captura
 * de corrida no adaptador nunca divirjam.
 */
public enum DuplicateAccountField {

    EMAIL("EMAIL_ALREADY_REGISTERED", "E-mail já cadastrado", "email", "uq_accounts_email_open"),
    TAX_ID("TAX_ID_ALREADY_REGISTERED", "Documento já cadastrado", "taxId", "uq_accounts_tax_id_open");

    private final String code;
    private final String message;
    private final String field;
    private final String constraintName;

    DuplicateAccountField(String code, String message, String field, String constraintName) {
        this.code = code;
        this.message = message;
        this.field = field;
        this.constraintName = constraintName;
    }

    public ConflictException toException() {
        return new ConflictException(code, message, field);
    }

    public static Optional<DuplicateAccountField> byConstraintName(String constraintName) {
        return Arrays.stream(values()).filter(f -> f.constraintName.equals(constraintName)).findFirst();
    }
}
