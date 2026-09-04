package com.paysi.catalog.appearance.adapter;

import com.paysi.catalog.appearance.domain.Appearance;
import com.paysi.catalog.appearance.port.AppearanceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcAppearanceRepository implements AppearanceRepository {
    private final JdbcTemplate jdbc;

    JdbcAppearanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Appearance> findByOfferId(UUID offerId) {
        return jdbc.query("SELECT * FROM offer_appearance WHERE offer_id = ?", (rs, row) ->
                new Appearance(rs.getObject("offer_id", UUID.class),
                        rs.getObject("logo_asset_id", UUID.class),
                        rs.getObject("banner_asset_id", UUID.class),
                        rs.getObject("side_image_asset_id", UUID.class),
                        rs.getString("primary_color"), rs.getString("button_text"),
                        rs.getTimestamp("updated_at").toInstant()), offerId).stream().findFirst();
    }

    @Override
    public void save(Appearance appearance) {
        jdbc.update("""
                INSERT INTO offer_appearance
                  (offer_id, logo_asset_id, banner_asset_id, side_image_asset_id,
                   primary_color, button_text, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (offer_id) DO UPDATE SET
                  logo_asset_id = EXCLUDED.logo_asset_id,
                  banner_asset_id = EXCLUDED.banner_asset_id,
                  side_image_asset_id = EXCLUDED.side_image_asset_id,
                  primary_color = EXCLUDED.primary_color,
                  button_text = EXCLUDED.button_text,
                  updated_at = EXCLUDED.updated_at
                """, appearance.offerId(), appearance.logoAssetId(), appearance.bannerAssetId(),
                appearance.sideImageAssetId(), appearance.primaryColor(), appearance.buttonText(),
                Timestamp.from(appearance.updatedAt()));
    }
}
