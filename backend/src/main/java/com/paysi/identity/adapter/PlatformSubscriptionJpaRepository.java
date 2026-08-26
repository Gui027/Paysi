package com.paysi.identity.adapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface PlatformSubscriptionJpaRepository extends JpaRepository<PlatformSubscriptionEntity, UUID> {
}
