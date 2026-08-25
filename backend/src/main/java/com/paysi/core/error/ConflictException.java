package com.paysi.core.error;

/** Estado já existente incompatível com a operação pedida. Mapeado para HTTP 409. */
public class ConflictException extends DomainException {
    public ConflictException(String code, String message, String field) {
        super(code, message, field);
    }
}
