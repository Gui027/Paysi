package com.paysi.catalog.coupon.domain;

import com.paysi.core.error.ValidationException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public record Coupon(
        UUID id,
        UUID sellerId,
        String code,
        CouponKind kind,
        int value,
        Instant startsAt,
        Instant expiresAt,
        Integer maxRedemptions,
        int maxPerBuyer,
        int redeemedCount,
        Set<UUID> offerIds,
        Instant archivedAt,
        Instant createdAt
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,31}$");

    public Coupon {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sellerId, "sellerId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw invalid("O código deve ter entre 3 e 32 letras, números, '-' ou '_'", "code");
        }
        if (kind == CouponKind.PERCENT && (value < 1 || value > 10_000)) {
            throw invalid("O desconto percentual deve estar entre 0,01% e 100%", "value");
        }
        if (kind == CouponKind.FIXED && value < 1) {
            throw invalid("O desconto fixo deve ser maior que zero", "value");
        }
        if (startsAt != null && expiresAt != null && !startsAt.isBefore(expiresAt)) {
            throw invalid("O início deve ser anterior ao vencimento", "expiresAt");
        }
        if (maxRedemptions != null && maxRedemptions < 1) {
            throw invalid("O limite de resgates deve ser maior que zero", "maxRedemptions");
        }
        if (maxPerBuyer < 1) {
            throw invalid("O limite por comprador deve ser maior que zero", "maxPerBuyer");
        }
        if (redeemedCount < 0) {
            throw invalid("O total resgatado não pode ser negativo", "redeemedCount");
        }
        if (offerIds == null || offerIds.isEmpty()) {
            throw invalid("Informe ao menos uma oferta", "offerIds");
        }
        offerIds = Set.copyOf(offerIds);
    }

    public static Coupon create(UUID id, UUID sellerId, String code, CouponValues values, Instant now) {
        return new Coupon(id, sellerId, code, values.kind(), values.value(), values.startsAt(),
                values.expiresAt(), values.maxRedemptions(), values.maxPerBuyer(), 0, values.offerIds(),
                null, now);
    }

    public Coupon update(CouponValues values) {
        return new Coupon(id, sellerId, code, values.kind(), values.value(), values.startsAt(),
                values.expiresAt(), values.maxRedemptions(), values.maxPerBuyer(), redeemedCount,
                values.offerIds(), archivedAt, createdAt);
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("COUPON_INVALID", message, field);
    }
}
