package com.paysi.affiliate.port;

import com.paysi.affiliate.domain.AffiliateProgram;

import java.util.Optional;
import java.util.UUID;

public interface AffiliateProgramRepository {
    Optional<AffiliateProgram> find(UUID productId);

    void save(AffiliateProgram program);
}
