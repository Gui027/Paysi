package com.paysi.identity.session.web.dto;

import com.paysi.identity.domain.InitialMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        InitialMode initialMode
) {
    public InitialMode resolvedMode() {
        return initialMode == null ? InitialMode.SELLER : initialMode;
    }

    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=[REDACTED], initialMode=%s]".formatted(email, initialMode);
    }
}
