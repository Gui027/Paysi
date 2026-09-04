package com.paysi.catalog.asset.app;

import com.paysi.catalog.asset.domain.Asset;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.asset.port.AssetStorage;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class AssetService {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXELS = 16_000_000L;

    private final AssetRepository assets;
    private final AssetStorage storage;
    private final Clock clock;
    private final String publicBaseUrl;

    public AssetService(AssetRepository assets, AssetStorage storage,
                        @Value("${paysi.asset.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this(assets, storage, Clock.systemUTC(), publicBaseUrl);
    }

    AssetService(AssetRepository assets, AssetStorage storage, Clock clock, String publicBaseUrl) {
        this.assets = assets;
        this.storage = storage;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public AssetView upload(UUID ownerId, AssetKind kind, byte[] content) {
        if (content == null || content.length == 0) {
            throw invalid("Envie uma imagem", "file");
        }
        if (content.length > MAX_BYTES) {
            throw invalid("A imagem deve ter no máximo 5 MB", "file");
        }
        ImageMetadata metadata = inspect(content);
        UUID id = UUID.randomUUID();
        String key = id + metadata.extension();
        storage.save(key, content);
        Asset asset = new Asset(id, ownerId, kind, key, metadata.contentType(), content.length,
                metadata.width(), metadata.height(), null, clock.instant());
        try {
            assets.insert(asset);
        } catch (RuntimeException error) {
            storage.delete(key);
            throw error;
        }
        return view(asset);
    }

    @Transactional(readOnly = true)
    public AssetDownload download(UUID assetId) {
        Asset asset = assets.findActive(assetId).orElseThrow(AssetService::notFound);
        return new AssetDownload(asset.contentType(), storage.read(asset.storageKey()));
    }

    @Transactional
    public void remove(UUID ownerId, UUID assetId) {
        Asset asset = assets.findActiveOwned(ownerId, assetId).orElseThrow(AssetService::notFound);
        if (!assets.archiveOwned(ownerId, assetId, clock.instant())) throw notFound();
        storage.delete(asset.storageKey());
    }

    public AssetView view(Asset asset) {
        return new AssetView(asset, publicBaseUrl + "/v1/assets/" + asset.id() + "/content");
    }

    private static ImageMetadata inspect(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) throw invalid("Arquivo de imagem inválido", "file");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Use uma imagem PNG ou JPEG válida", "file");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String contentType;
                String extension;
                if (format.equals("png")) {
                    contentType = "image/png";
                    extension = ".png";
                } else if (format.equals("jpeg") || format.equals("jpg")) {
                    contentType = "image/jpeg";
                    extension = ".jpg";
                } else {
                    throw invalid("Use uma imagem PNG ou JPEG válida", "file");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw invalid("A imagem deve ter até 4096 px e 16 megapixels", "file");
                }
                return new ImageMetadata(contentType, extension, width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw invalid("Arquivo de imagem inválido", "file");
        }
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("ASSET_INVALID", message, field);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("ASSET_NOT_FOUND", "Ativo não encontrado");
    }

    private record ImageMetadata(String contentType, String extension, int width, int height) {
    }
}
