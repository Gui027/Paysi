package com.paysi.identity.web.dto;

import com.paysi.identity.app.AccountCreated;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PersonType;

import java.util.UUID;

public record AccountResponse(
        UUID accountId,
        String fullName,
        String email,
        PersonType personType,
        KycStatus kycStatus,
        InitialMode activeMode,
        String plan
) {
    public static AccountResponse from(AccountCreated created) {
        return new AccountResponse(created.accountId(), created.fullName(), created.email(),
                created.personType(), created.kycStatus(), created.activeMode(), created.plan());
    }
}
