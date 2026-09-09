package com.paysi.catalog.asset.app;

import com.paysi.catalog.asset.domain.Asset;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.asset.port.AssetStorage;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetServiceTest {
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private final Assets repository = new Assets();
    private final MemoryStorage storage = new MemoryStorage();
    private final AssetService service = new AssetService(repository, storage,
            Clock.fixed(NOW, ZoneOffset.UTC), "https://api.paysi.test/");

    @Test
    void storesRealImageMetadataAndServesPaysiUrl() throws Exception {
        byte[] png = image("png", 120, 80);

        AssetView result = service.upload(OWNER, AssetKind.LOGO, png);

        assertThat(result.asset().contentType()).isEqualTo("image/png");
        assertThat(result.asset().width()).isEqualTo(120);
        assertThat(result.asset().height()).isEqualTo(80);
        assertThat(result.url()).isEqualTo("https://api.paysi.test/v1/assets/"
                + result.asset().id() + "/content");
        assertThat(service.download(result.asset().id()).content()).isEqualTo(png);
    }

    @Test
    void rejectsSpoofedMimeOversizeAndDimensionLimits() throws Exception {
        assertThatThrownBy(() -> service.upload(OWNER, AssetKind.LOGO,
                "<script>alert(1)</script>".getBytes()))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.code()).isEqualTo("ASSET_INVALID"));

        assertThatThrownBy(() -> service.upload(OWNER, AssetKind.LOGO,
                new byte[AssetService.MAX_BYTES + 1]))
                .isInstanceOf(ValidationException.class);

        assertThatThrownBy(() -> service.upload(OWNER, AssetKind.BANNER,
                image("png", AssetService.MAX_DIMENSION + 1, 1)))
                .isInstanceOf(ValidationException.class);
        assertThat(repository.rows).isEmpty();
    }

    @Test
    void onlyOwnerCanRemoveAndRemovalMakesContentUnavailable() throws Exception {
        UUID assetId = service.upload(OWNER, AssetKind.SIDE_IMAGE, image("jpg", 10, 10)).asset().id();

        assertThatThrownBy(() -> service.remove(OTHER, assetId)).isInstanceOf(NotFoundException.class);
        assertThat(repository.rows).containsKey(assetId);

        service.remove(OWNER, assetId);

        assertThatThrownBy(() -> service.download(assetId)).isInstanceOf(NotFoundException.class);
        assertThat(storage.rows).isEmpty();
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }

    private static final class Assets implements AssetRepository {
        private final Map<UUID, Asset> rows = new HashMap<>();

        public void insert(Asset asset) { rows.put(asset.id(), asset); }
        public Optional<Asset> findActive(UUID assetId) {
            return Optional.ofNullable(rows.get(assetId)).filter(asset -> asset.archivedAt() == null);
        }
        public Optional<Asset> findActiveOwned(UUID ownerId, UUID assetId) {
            return findActive(assetId).filter(asset -> asset.ownerId().equals(ownerId));
        }
        public boolean archiveOwned(UUID ownerId, UUID assetId, Instant archivedAt) {
            var found = findActiveOwned(ownerId, assetId);
            found.ifPresent(asset -> rows.put(assetId, new Asset(asset.id(), asset.ownerId(), asset.kind(),
                    asset.storageKey(), asset.contentType(), asset.byteSize(), asset.width(), asset.height(),
                    archivedAt, asset.createdAt())));
            return found.isPresent();
        }
    }

    private static final class MemoryStorage implements AssetStorage {
        private final Map<String, byte[]> rows = new HashMap<>();
        public void save(String key, byte[] content) { rows.put(key, content.clone()); }
        public byte[] read(String key) { return rows.get(key).clone(); }
        public void delete(String key) { rows.remove(key); }
    }
}
