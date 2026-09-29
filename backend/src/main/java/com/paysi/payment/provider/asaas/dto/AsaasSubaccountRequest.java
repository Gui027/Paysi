package com.paysi.payment.provider.asaas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Corpo de POST /v3/accounts (criação de subconta white-label). Confirmado em produção quais campos a
 * Asaas exige no mínimo: nome, e-mail, documento, CEP, data de nascimento e renda/faturamento. Campos que
 * ela pode exigir a mais (telefone, tipo de empresa para CNPJ) ficam de fora de propósito — melhor a Asaas
 * recusar com um erro claro de campo faltando do que a Paysi inventar um valor.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsaasSubaccountRequest(String name, String email, String cpfCnpj, String postalCode,
                                     LocalDate birthDate, BigDecimal incomeValue) {
}
