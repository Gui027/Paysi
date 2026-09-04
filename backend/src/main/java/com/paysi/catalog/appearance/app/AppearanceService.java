package com.paysi.catalog.appearance.app;

import com.paysi.catalog.appearance.domain.Appearance;
import com.paysi.catalog.appearance.port.AppearanceRepository;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AppearanceService {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final OfferRepository offers;
    private final AssetRepository assets;
    private final AppearanceRepository appearances;
    private final Clock clock;

    public AppearanceService(OfferRepository offers, AssetRepository assets,
                             AppearanceRepository appearances) {
        this(offers, assets, appearances, Clock.systemUTC());
    }

    AppearanceService(OfferRepository offers, AssetRepository assets,
                      AppearanceRepository appearances, Clock clock) {
        this.offers = offers;
        this.assets = assets;
        this.appearances = appearances;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Appearance get(UUID sellerId, UUID offerId) {
        ownedOffer(sellerId, offerId);
        return appearances.findByOfferId(offerId).orElseGet(() -> Appearance.defaults(offerId, clock.instant()));
    }

    @Transactional
    public Appearance update(UUID sellerId, UUID offerId, AppearanceCommand command) {
        ownedOffer(sellerId, offerId);
        validateAsset(sellerId, command.logoAssetId(), AssetKind.LOGO, "logoAssetId");
        validateAsset(sellerId, command.bannerAssetId(), AssetKind.BANNER, "bannerAssetId");
        validateAsset(sellerId, command.sideImageAssetId(), AssetKind.SIDE_IMAGE, "sideImageAssetId");
        String color = command.primaryColor() == null ? "#2563EB" : command.primaryColor().toUpperCase();
        String buttonText = command.buttonText() == null ? "Comprar agora" : command.buttonText().trim();
        if (!COLOR.matcher(color).matches()) {
            throw invalid("Use uma cor hexadecimal no formato #RRGGBB", "primaryColor");
        }
        if (buttonText.isEmpty() || buttonText.length() > 40) {
            throw invalid("O texto do botão deve ter entre 1 e 40 caracteres", "buttonText");
        }
        Appearance appearance = new Appearance(offerId, command.logoAssetId(), command.bannerAssetId(),
                command.sideImageAssetId(), color, buttonText, clock.instant());
        appearances.save(appearance);
        return appearance;
    }

    private void validateAsset(UUID sellerId, UUID assetId, AssetKind expected, String field) {
        if (assetId == null) return;
        var asset = assets.findActiveOwned(sellerId, assetId)
                .orElseThrow(() -> invalid("Ativo não encontrado para esta conta", field));
        if (asset.kind() != expected) throw invalid("Tipo de ativo incompatível", field);
    }

    private void ownedOffer(UUID sellerId, UUID offerId) {
        offers.findActiveOwned(sellerId, offerId).orElseThrow(() ->
                new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("APPEARANCE_INVALID", message, field);
    }
}
