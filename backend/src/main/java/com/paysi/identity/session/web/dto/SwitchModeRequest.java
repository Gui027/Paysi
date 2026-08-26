package com.paysi.identity.session.web.dto;

import com.paysi.identity.domain.InitialMode;
import jakarta.validation.constraints.NotNull;

public record SwitchModeRequest(@NotNull InitialMode mode) {
}
