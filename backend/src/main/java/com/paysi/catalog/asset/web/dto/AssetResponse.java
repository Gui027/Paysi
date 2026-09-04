package com.paysi.catalog.asset.web.dto;

import com.paysi.catalog.asset.app.AssetView;
import com.paysi.catalog.asset.domain.AssetKind;

import java.util.UUID;

public record AssetResponse(
        UUID id,
        AssetKind kind,
        String contentType,
        long byteSize,
        int width,
        int height,
        String url
) {
    public static AssetResponse from(AssetView view) {
        var asset = view.asset();
        return new AssetResponse(asset.id(), asset.kind(), asset.contentType(), asset.byteSize(),
                asset.width(), asset.height(), view.url());
    }
}
