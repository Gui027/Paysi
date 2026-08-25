package com.paysi.identity.app;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PersonType;

import java.util.UUID;

/** Resultado do cadastro, já com o plano confirmado — nunca carrega a senha ou seu hash. */
public record AccountCreated(
        UUID accountId,
        String fullName,
        String email,
        PersonType personType,
        KycStatus kycStatus,
        InitialMode activeMode,
        String plan
) {
}
