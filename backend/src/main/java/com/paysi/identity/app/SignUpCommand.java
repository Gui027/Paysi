package com.paysi.identity.app;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.PersonType;

/** Entrada do caso de uso de cadastro, já validada em formato pelo controlador. */
public record SignUpCommand(
        String fullName,
        String email,
        String rawPassword,
        PersonType personType,
        String taxId,
        InitialMode initialMode,
        String termsHash
) {
    @Override
    public String toString() {
        // AM-13 / lista de revisão #20: a senha em texto puro nunca aparece em log.
        return "SignUpCommand[fullName=%s, email=%s, rawPassword=[REDACTED], personType=%s, taxId=[REDACTED], initialMode=%s, termsHash=%s]"
                .formatted(fullName, email, personType, initialMode, termsHash);
    }
}
