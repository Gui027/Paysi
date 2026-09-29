package com.paysi.payment.provider;

/** Cria a subconta do provedor (hoje só a Asaas) para um vendedor/afiliado poder receber split de verdade. */
public interface SubaccountProvider {
    SubaccountResult createSubaccount(String name, String email, String taxIdDigits);

    record SubaccountResult(String accountId, String walletId) {
    }

    /** Documento inválido, campo obrigatório faltando ou qualquer outra recusa do provedor ao criar a subconta. */
    class SubaccountCreationException extends RuntimeException {
        public SubaccountCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
