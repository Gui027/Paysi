package com.paysi.identity.web.dto;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.PersonType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** RF-001/RF-005: contrato de entrada de {@code POST /v1/accounts}. */
public record CreateAccountRequest(
        @NotBlank(message = "Nome é obrigatório")
        String fullName,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, max = 128, message = "Senha deve ter entre 8 e 128 caracteres")
        String password,

        @NotNull(message = "Tipo de pessoa é obrigatório")
        PersonType personType,

        @NotBlank(message = "Documento é obrigatório")
        String taxId,

        @NotNull(message = "Modo inicial é obrigatório")
        InitialMode initialMode,

        @NotBlank(message = "Aceite dos termos é obrigatório")
        String termsHash
) {
    @Override
    public String toString() {
        // Lista de revisão #20: nenhuma senha em texto puro entra em log.
        return "CreateAccountRequest[fullName=%s, email=%s, password=[REDACTED], personType=%s, taxId=[REDACTED], initialMode=%s, termsHash=%s]"
                .formatted(fullName, email, personType, initialMode, termsHash);
    }
}
