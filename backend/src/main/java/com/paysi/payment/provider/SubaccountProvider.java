package com.paysi.payment.provider;

import java.time.LocalDate;

/** Cria a subconta do provedor (hoje só a Asaas) para um vendedor/afiliado poder receber split de verdade. */
public interface SubaccountProvider {
    SubaccountResult createSubaccount(String name, String email, String taxIdDigits, String postalCode, LocalDate birthDate, long incomeValueCents);

    record SubaccountResult(String accountId, String walletId) {
    }

    /** Documento inválido, campo obrigatório faltando ou qualquer outra recusa do provedor ao criar a subconta. */
    class SubaccountCreationException extends RuntimeException {
        public SubaccountCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
