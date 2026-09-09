package com.paysi.catalog.appearance.app;

import java.util.UUID;

public record AppearanceCommand(
        UUID logoAssetId,
        UUID bannerAssetId,
        UUID sideImageAssetId,
        String primaryColor,
        String buttonText
) {
}
