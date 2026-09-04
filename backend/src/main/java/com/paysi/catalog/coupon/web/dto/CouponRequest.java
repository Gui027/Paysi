package com.paysi.catalog.coupon.web.dto;

import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.coupon.domain.CouponValues;
import com.paysi.core.error.ValidationException;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CouponRequest(
        @NotBlank String code,
        @NotNull CouponKind discountType,
        Integer discountBps,
        Integer discountCents,
        Instant startsAt,
        Instant expiresAt,
        Integer maxRedemptions,
        @NotNull @Min(1) Integer maxPerBuyer,
        @NotEmpty Set<UUID> offerIds
) {
    public CouponValues toValues() {
        int value = switch (discountType) {
            case PERCENT -> {
                if (discountBps == null) {
                    throw invalid("Informe o valor percentual em basis points", "discountBps");
                }
                if (discountCents != null) {
                    throw invalid("Cupom percentual não pode informar valor fixo", "discountCents");
                }
                yield discountBps;
            }
            case FIXED -> {
                if (discountCents == null) {
                    throw invalid("Informe o valor fixo em centavos", "discountCents");
                }
                if (discountBps != null) {
                    throw invalid("Cupom de valor fixo não pode informar percentual", "discountBps");
                }
                yield discountCents;
            }
        };
        return new CouponValues(discountType, value, startsAt, expiresAt, maxRedemptions, maxPerBuyer,
                offerIds);
    }

    private static ValidationException invalid(String message, String field) {
        return new ValidationException("COUPON_INVALID", message, field);
    }
}
