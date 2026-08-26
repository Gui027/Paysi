package com.paysi.identity.recovery.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryRequest(@NotBlank @Email String email) {
}
