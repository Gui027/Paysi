package com.paysi.catalog.appearance.web.dto;

import com.paysi.catalog.appearance.app.AppearanceCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AppearanceRequest(
        UUID logoAssetId,
        UUID bannerAssetId,
        UUID sideImageAssetId,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use uma cor no formato #RRGGBB")
        String primaryColor,
        @Size(min = 1, max = 40, message = "O texto do botão deve ter entre 1 e 40 caracteres")
        String buttonText
) {
    public AppearanceCommand toCommand() {
        return new AppearanceCommand(logoAssetId, bannerAssetId, sideImageAssetId,
                primaryColor, buttonText);
    }
}
