package com.paysi.catalog.asset.port;

import com.paysi.catalog.asset.domain.Asset;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository {
    void insert(Asset asset);

    Optional<Asset> findActive(UUID assetId);

    Optional<Asset> findActiveOwned(UUID ownerId, UUID assetId);

    boolean archiveOwned(UUID ownerId, UUID assetId, Instant archivedAt);
}
