package com.paysi.catalog.offer.port;

import java.util.UUID;

public interface PublicationRequirementRepository {
    boolean hasValidatedFiscalProfile(UUID sellerId);
}
