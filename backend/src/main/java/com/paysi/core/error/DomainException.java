package com.paysi.core.error;

/**
 * Base para erros de regra de negócio que a camada web traduz em resposta HTTP
 * com {@code code}, {@code message} e {@code field} (documento 2, §4.1).
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final String field;

    protected DomainException(String code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    /** Campo do corpo da requisição associado ao erro, ou {@code null} se não houver um só. */
    public String field() {
        return field;
    }
}
