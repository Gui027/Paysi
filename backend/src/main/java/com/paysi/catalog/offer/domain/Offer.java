package com.paysi.catalog.offer.domain;

import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.core.error.ValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record Offer(
        UUID id,
        UUID productId,
        ChargeType chargeType,
        Segment segment,
        String slug,
        long priceCents,
        BillingCycle cycle,
        int trialDays,
        boolean trialRequiresCard,
        int guaranteeDays,
        int maxInstallments,
        int boletoDueDays,
        int boletoAdvanceDays,
        Set<OfferPaymentMethod> paymentMethods,
        OfferPayoutDelay payoutDelay,
        OfferStatus status,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public Offer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(chargeType, "chargeType");
        Objects.requireNonNull(segment, "segment");
        if (slug == null || slug.isBlank()) throw invalid("O slug é obrigatório", "slug");
        if (priceCents < 2_000) throw invalid("O preço mínimo é 2000 centavos", "priceCents");
        if (chargeType == ChargeType.SUBSCRIPTION && cycle == null) {
            throw invalid("Ciclo é obrigatório para assinaturas", "cycle");
        }
        if (chargeType == ChargeType.ONE_TIME && cycle != null) {
            throw invalid("Ciclo só pode ser informado para assinaturas", "cycle");
        }
        if (trialDays < 0 || trialDays > 30) {
            throw invalid("Período de teste deve estar entre 0 e 30 dias", "trialDays");
        }
        if (!trialRequiresCard && segment != Segment.SAAS) {
            throw invalid("Teste sem cartão está disponível apenas para SaaS", "trialRequiresCard");
        }
        if (guaranteeDays < 7) throw invalid("Garantia mínima é de 7 dias", "guaranteeDays");
        if (maxInstallments < 1 || maxInstallments > 12) {
            throw invalid("Parcelamento deve estar entre 1 e 12 vezes", "maxInstallments");
        }
        if (boletoDueDays < 1 || boletoDueDays > 15) {
            throw invalid("Vencimento do boleto deve estar entre 1 e 15 dias", "boletoDueDays");
        }
        if (boletoAdvanceDays < 3 || boletoAdvanceDays > 10) {
            throw invalid("Antecedência do boleto deve estar entre 3 e 10 dias", "boletoAdvanceDays");
        }
        if (paymentMethods == null || paymentMethods.isEmpty()) {
            throw invalid("Informe ao menos um meio de pagamento", "paymentMethods");
        }
        paymentMethods = Set.copyOf(paymentMethods);
        if (segment != Segment.SAAS && paymentMethods.contains(OfferPaymentMethod.BOLETO)) {
            throw invalid("Boleto está disponível apenas para SaaS", "paymentMethods");
        }
        Objects.requireNonNull(payoutDelay, "payoutDelay");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Offer create(UUID id, UUID productId, ChargeType chargeType, Segment segment,
                               String slug, OfferValues values, Instant now) {
        return new Offer(id, productId, chargeType, segment, slug, values.priceCents(), values.cycle(),
                values.trialDays(), values.trialRequiresCard(), values.guaranteeDays(),
                values.maxInstallments(), values.boletoDueDays(), values.boletoAdvanceDays(),
                values.paymentMethods(), values.payoutDelay(), OfferStatus.DRAFT, null, now, now);
    }

    public Offer update(OfferValues values, Instant now) {
        return new Offer(id, productId, chargeType, segment, slug, values.priceCents(), values.cycle(),
                values.trialDays(), values.trialRequiresCard(), values.guaranteeDays(),
                values.maxInstallments(), values.boletoDueDays(), values.boletoAdvanceDays(),
                values.paymentMethods(), values.payoutDelay(), status, archivedAt, createdAt, now);
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("OFFER_INVALID", message, field);
    }
}
