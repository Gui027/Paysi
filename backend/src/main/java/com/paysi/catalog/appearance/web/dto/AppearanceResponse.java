package com.paysi.catalog.appearance.web.dto;

import com.paysi.catalog.appearance.domain.Appearance;

import java.time.Instant;
import java.util.UUID;

public record AppearanceResponse(
        UUID logoAssetId,
        UUID bannerAssetId,
        UUID sideImageAssetId,
        String primaryColor,
        String buttonText,
        Instant updatedAt
) {
    public static AppearanceResponse from(Appearance appearance) {
        return new AppearanceResponse(appearance.logoAssetId(), appearance.bannerAssetId(),
                appearance.sideImageAssetId(), appearance.primaryColor(), appearance.buttonText(),
                appearance.updatedAt());
    }
}
