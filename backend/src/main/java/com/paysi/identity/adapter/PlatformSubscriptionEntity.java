package com.paysi.identity.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Leitura de {@code platform_subscriptions} (V022) — fonte única do plano comercial.
 * Só o necessário para confirmar e devolver o plano no cadastro; a gestão completa
 * do plano pertence ao futuro módulo billing.
 */
@Entity
@Table(name = "platform_subscriptions")
class PlatformSubscriptionEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false)
    private String plan;

    protected PlatformSubscriptionEntity() {
        // exigido pelo JPA
    }

    String plan() {
        return plan;
    }
}
