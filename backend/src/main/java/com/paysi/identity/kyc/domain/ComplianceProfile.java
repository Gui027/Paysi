package com.paysi.identity.kyc.domain;

import java.time.LocalDate;

/** CEP e data de nascimento — exigidos pela Asaas para criar a subconta do vendedor/afiliado. */
public record ComplianceProfile(String postalCode, LocalDate birthDate) {
    public boolean complete() {
        return postalCode != null && !postalCode.isBlank() && birthDate != null;
    }
}
