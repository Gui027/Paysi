package com.paysi.catalog.appearance.port;

import com.paysi.catalog.appearance.domain.Appearance;

import java.util.Optional;
import java.util.UUID;

public interface AppearanceRepository {
    Optional<Appearance> findByOfferId(UUID offerId);

    void save(Appearance appearance);
}
