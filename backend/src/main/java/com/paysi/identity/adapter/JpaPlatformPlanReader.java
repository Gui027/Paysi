package com.paysi.identity.adapter;

import com.paysi.identity.port.PlatformPlanReader;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JpaPlatformPlanReader implements PlatformPlanReader {

    private final PlatformSubscriptionJpaRepository jpaRepository;

    JpaPlatformPlanReader(PlatformSubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public String currentPlan(UUID accountId) {
        return jpaRepository.findById(accountId)
                .map(PlatformSubscriptionEntity::plan)
                .orElseThrow(() -> new IllegalStateException(
                        "Conta " + accountId + " sem platform_subscriptions — RF-125 violada"));
    }
}
