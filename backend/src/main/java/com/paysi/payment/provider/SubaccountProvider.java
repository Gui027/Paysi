package com.paysi.payment.provider;

import java.time.LocalDate;
import java.util.List;

/** Cria a subconta do provedor (hoje só a Asaas) para um vendedor/afiliado poder receber split de verdade. */
public interface SubaccountProvider {
    SubaccountResult createSubaccount(String name, String email, String taxIdDigits, String postalCode, LocalDate birthDate, long incomeValueCents);

    /**
     * Gera uma nova credencial para uma subconta já existente, localizada pelo walletId. Usado somente
     * para reparar contas legadas cuja chave retornada na criação não foi armazenada.
     */
    String recoverAccessToken(String walletId);

    /** Documentos de verificação pendentes da subconta. Chamado com a chave de API própria dela, não a da Paysi. */
    List<PendingDocument> pendingDocuments(String subaccountApiKey);

    /** Situação cadastral atual da subconta, consultada com a chave própria dela. */
    SubaccountStatus accountStatus(String subaccountApiKey);

    /** Envia um documento para o grupo (id vindo de {@link #pendingDocuments}); só aceito quando {@code externalUrl} é nulo. */
    void submitDocument(String subaccountApiKey, String documentGroupId, String documentType, byte[] file, String filename, String contentType);

    /** {@code apiKey}: chave própria da subconta — a Asaas só devolve isso na criação, nunca mais depois. */
    record SubaccountResult(String accountId, String walletId, String apiKey) {
    }

    /** {@code externalUrl} presente = só dá pra enviar por aquele link (fora da Paysi); ausente = envia por {@link #submitDocument}. */
    record PendingDocument(String id, String status, String type, String description, String externalUrl) {
    }

    record SubaccountStatus(String general, String commercialInfo, String bankAccountInfo, String documentation) {
        public boolean approved() {
            return "APPROVED".equalsIgnoreCase(general);
        }

        public boolean rejected() {
            return "REJECTED".equalsIgnoreCase(general)
                    || "REJECTED".equalsIgnoreCase(commercialInfo)
                    || "REJECTED".equalsIgnoreCase(bankAccountInfo)
                    || "REJECTED".equalsIgnoreCase(documentation);
        }
    }

    /** Documento inválido, campo obrigatório faltando ou qualquer outra recusa do provedor ao criar a subconta. */
    class SubaccountCreationException extends RuntimeException {
        public SubaccountCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
