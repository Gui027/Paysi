package com.paysi.catalog.product.domain;

import com.paysi.core.error.ValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(
        UUID id,
        UUID sellerId,
        String name,
        String description,
        Segment segment,
        ChargeType chargeType,
        boolean affiliationEnabled,
        ProductStatus status,
        Instant archivedAt,
        Instant createdAt
) {
    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(chargeType, "chargeType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Product createDraft(UUID id, UUID sellerId, String name, String description,
                                      Segment segment, ChargeType chargeType,
                                      boolean affiliationEnabled, Instant createdAt) {
        return new Product(id, sellerId, normalizeName(name), normalizeDescription(description),
                requireSegment(segment), requireChargeType(chargeType), affiliationEnabled,
                ProductStatus.DRAFT, null, createdAt);
    }

    public Product update(String name, String description, Segment segment, ChargeType chargeType,
                          boolean affiliationEnabled) {
        return new Product(id, sellerId, normalizeName(name), normalizeDescription(description),
                requireSegment(segment), requireChargeType(chargeType), affiliationEnabled,
                status, archivedAt, createdAt);
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("PRODUCT_NAME_REQUIRED", "Nome do produto é obrigatório", "name");
        }
        String normalized = value.strip();
        if (normalized.length() > 120) {
            throw new ValidationException("PRODUCT_NAME_TOO_LONG",
                    "Nome do produto deve ter no máximo 120 caracteres", "name");
        }
        return normalized;
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > 2000) {
            throw new ValidationException("PRODUCT_DESCRIPTION_TOO_LONG",
                    "Descrição deve ter no máximo 2000 caracteres", "description");
        }
        return normalized;
    }

    private static Segment requireSegment(Segment value) {
        if (value == null) {
            throw new ValidationException("PRODUCT_SEGMENT_REQUIRED", "Segmento é obrigatório", "segment");
        }
        return value;
    }

    private static ChargeType requireChargeType(ChargeType value) {
        if (value == null) {
            throw new ValidationException("PRODUCT_CHARGE_TYPE_REQUIRED",
                    "Tipo de cobrança é obrigatório", "chargeType");
        }
        return value;
    }
}
