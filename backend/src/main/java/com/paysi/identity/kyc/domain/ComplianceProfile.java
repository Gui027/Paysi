package com.paysi.identity.kyc.domain;

import java.time.LocalDate;

/** CEP, data de nascimento e renda/faturamento — exigidos pela Asaas para criar a subconta do vendedor/afiliado. */
public record ComplianceProfile(String postalCode, LocalDate birthDate, Long incomeValueCents) {
    public boolean complete() {
        return postalCode != null && !postalCode.isBlank() && birthDate != null && incomeValueCents != null;
    }
}
