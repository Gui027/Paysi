package com.paysi.catalog.appearance.app;

import com.paysi.catalog.appearance.domain.Appearance;
import com.paysi.catalog.appearance.port.AppearanceRepository;
import com.paysi.catalog.asset.domain.Asset;
import com.paysi.catalog.asset.domain.AssetKind;
import com.paysi.catalog.asset.port.AssetRepository;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.port.OfferRepository;
import com.paysi.core.error.NotFoundException;
import com.paysi.core.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppearanceServiceTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    private final OfferRepository offers = mock(OfferRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final AppearanceRepository appearances = mock(AppearanceRepository.class);
    private final AppearanceService service = new AppearanceService(offers, assets, appearances,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void updatesOnlyOwnedAssetsWithExpectedKinds() {
        UUID logoId = UUID.randomUUID();
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(mock(Offer.class)));
        when(assets.findActiveOwned(SELLER, logoId)).thenReturn(Optional.of(asset(logoId, AssetKind.LOGO)));

        Appearance result = service.update(SELLER, OFFER_ID,
                new AppearanceCommand(logoId, null, null, "#aabbcc", "  Assinar  "));

        assertThat(result.primaryColor()).isEqualTo("#AABBCC");
        assertThat(result.buttonText()).isEqualTo("Assinar");
        verify(appearances).save(result);
    }

    @Test
    void hidesForeignOfferAndRejectsForeignOrWrongKindAsset() {
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(SELLER, OFFER_ID)).isInstanceOf(NotFoundException.class);

        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(mock(Offer.class)));
        UUID assetId = UUID.randomUUID();
        when(assets.findActiveOwned(SELLER, assetId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(SELLER, OFFER_ID,
                new AppearanceCommand(assetId, null, null, "#000000", "Comprar")))
                .isInstanceOf(ValidationException.class);

        when(assets.findActiveOwned(SELLER, assetId)).thenReturn(Optional.of(asset(assetId, AssetKind.BANNER)));
        assertThatThrownBy(() -> service.update(SELLER, OFFER_ID,
                new AppearanceCommand(assetId, null, null, "#000000", "Comprar")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void suppliesSafeDefaultsWithoutPersistingThem() {
        when(offers.findActiveOwned(SELLER, OFFER_ID)).thenReturn(Optional.of(mock(Offer.class)));
        when(appearances.findByOfferId(OFFER_ID)).thenReturn(Optional.empty());

        Appearance result = service.get(SELLER, OFFER_ID);

        assertThat(result.primaryColor()).isEqualTo("#2563EB");
        assertThat(result.buttonText()).isEqualTo("Comprar agora");
    }

    private static Asset asset(UUID id, AssetKind kind) {
        return new Asset(id, SELLER, kind, id + ".png", "image/png", 10, 10, 10, null, NOW);
    }
}
