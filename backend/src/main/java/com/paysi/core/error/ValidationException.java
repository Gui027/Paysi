package com.paysi.core.error;

/** Regra de negócio violada por um valor de entrada. Mapeado para HTTP 400. */
public class ValidationException extends DomainException {
    public ValidationException(String code, String message, String field) {
        super(code, message, field);
    }
}
