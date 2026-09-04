package com.paysi.catalog.asset.domain;

import java.time.Instant;
import java.util.UUID;

public record Asset(
        UUID id,
        UUID ownerId,
        AssetKind kind,
        String storageKey,
        String contentType,
        long byteSize,
        int width,
        int height,
        Instant archivedAt,
        Instant createdAt
) {
}
