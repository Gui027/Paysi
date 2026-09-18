package com.paysi.core.error;

/**
 * Limite de tentativas excedido por chave, IP, CPF ou impressão de dispositivo
 * (AM-05, AM-22). Mapeado para HTTP 429.
 */
public class TooManyRequestsException extends DomainException {
    public TooManyRequestsException(String code, String message, String field) {
        super(code, message, field);
    }
}
