package com.paysi.core.error;

/** Identidade conhecida sem permissão para a operação. Mapeada para HTTP 403. */
public class ForbiddenException extends DomainException {
    public ForbiddenException(String code, String message) {
        super(code, message, null);
    }
}
