package com.paysi.catalog.asset.adapter;

import com.paysi.catalog.asset.domain.Asset;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAssetRepository implements AssetRepository {
    private final JdbcTemplate jdbc;

    JdbcAssetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Asset asset) {
        jdbc.update("""
                INSERT INTO assets
                  (id, owner_id, kind, storage_key, content_type, byte_size, width, height, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, asset.id(), asset.ownerId(), asset.kind().name(), asset.storageKey(),
                asset.contentType(), asset.byteSize(), asset.width(), asset.height(),
                Timestamp.from(asset.createdAt()));
    }

    @Override
    public Optional<Asset> findActive(UUID assetId) {
        return jdbc.query("SELECT * FROM assets WHERE id = ? AND archived_at IS NULL",
                (rs, row) -> map(rs), assetId).stream().findFirst();
    }

    @Override
    public Optional<Asset> findActiveOwned(UUID ownerId, UUID assetId) {
        return jdbc.query("SELECT * FROM assets WHERE owner_id = ? AND id = ? AND archived_at IS NULL",
                (rs, row) -> map(rs), ownerId, assetId).stream().findFirst();
    }

    @Override
    public boolean archiveOwned(UUID ownerId, UUID assetId, Instant archivedAt) {
        jdbc.update("""
                UPDATE offer_appearance
                   SET logo_asset_id = CASE WHEN logo_asset_id = ? THEN NULL ELSE logo_asset_id END,
                       banner_asset_id = CASE WHEN banner_asset_id = ? THEN NULL ELSE banner_asset_id END,
                       side_image_asset_id = CASE WHEN side_image_asset_id = ? THEN NULL ELSE side_image_asset_id END,
                       updated_at = ?
                 WHERE (logo_asset_id = ? OR banner_asset_id = ? OR side_image_asset_id = ?)
                   AND EXISTS (SELECT 1 FROM assets a WHERE a.id = ? AND a.owner_id = ?)
                """, assetId, assetId, assetId, Timestamp.from(archivedAt),
                assetId, assetId, assetId, assetId, ownerId);
        return jdbc.update("""
                UPDATE assets SET archived_at = ?
                 WHERE owner_id = ? AND id = ? AND archived_at IS NULL
                """, Timestamp.from(archivedAt), ownerId, assetId) == 1;
    }

    private static Asset map(ResultSet rs) throws SQLException {
        Timestamp archivedAt = rs.getTimestamp("archived_at");
        return new Asset(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                AssetKind.valueOf(rs.getString("kind")), rs.getString("storage_key"),
                rs.getString("content_type"), rs.getLong("byte_size"), rs.getInt("width"),
                rs.getInt("height"), archivedAt == null ? null : archivedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
