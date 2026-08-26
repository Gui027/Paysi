package com.paysi.identity.recovery.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 128) String newPassword,
        @NotBlank @Size(min = 8, max = 128) String confirmPassword
) {
    @Override
    public String toString() {
        return "PasswordResetRequest[token=[REDACTED], newPassword=[REDACTED], confirmPassword=[REDACTED]]";
    }
}
