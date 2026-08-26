package com.paysi.core.error;

/** Ausência ou falha de autenticação. Mapeada para HTTP 401. */
public class UnauthorizedException extends DomainException {
    public UnauthorizedException(String code, String message) {
        super(code, message, null);
    }
}
