package com.paysi.fiscal.issuer;

/**
 * BE-14.1: porta para o parceiro fiscal (emissor técnico de NFS-e), no mesmo espírito de
 * {@link com.paysi.payment.provider.PaymentProvider} — a variabilidade de prefeitura,
 * certificado e regra municipal (ADR-11) fica atrás desta interface e nunca vaza para o
 * domínio. Nenhuma chamada aqui acontece no caminho de confirmação do pagamento (RF-113).
 */
public interface InvoiceIssuer {
    /** RF-095: valida a credencial em ambiente de homologação antes de habilitar a emissão. */
    IssuerValidationResult validate(IssuerCredentials credentials);

    InvoiceIssueResult issue(InvoiceIssueRequest request);

    InvoiceCancelResult cancel(InvoiceCancelRequest request);
}
