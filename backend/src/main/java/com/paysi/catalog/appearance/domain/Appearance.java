package com.paysi.catalog.appearance.domain;

import java.time.Instant;
import java.util.UUID;

public record Appearance(
        UUID offerId,
        UUID logoAssetId,
        UUID bannerAssetId,
        UUID sideImageAssetId,
        String primaryColor,
        String buttonText,
        Instant updatedAt
) {
    public static Appearance defaults(UUID offerId, Instant now) {
        return new Appearance(offerId, null, null, null, "#2563EB", "Comprar agora", now);
    }
}
