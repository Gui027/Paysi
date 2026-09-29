package com.paysi.payment.provider.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo de POST /v3/accounts (criação de subconta white-label). Só os campos que temos certeza
 * serem aceitos hoje: nome, e-mail e documento. Campos que a Asaas pode exigir a mais (telefone,
 * endereço, tipo de empresa para CNPJ) ficam de fora de propósito — melhor a Asaas recusar com um
 * erro claro de campo faltando do que a Paysi inventar um valor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasSubaccountRequest(String name, String email, String cpfCnpj) {
}
