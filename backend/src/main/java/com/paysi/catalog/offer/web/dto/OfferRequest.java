package com.paysi.catalog.offer.web.dto;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferValues;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

import java.util.Set;
import java.util.UUID;

public record OfferRequest(
        @NotNull @Min(2000) Long priceCents,
        BillingCycle cycle,
        @NotNull @Min(0) @Max(30) Integer trialDays,
        @NotNull Boolean trialRequiresCard,
        @NotNull @Min(7) Integer guaranteeDays,
        @NotNull @Min(1) @Max(12) Integer maxInstallments,
        @NotNull @Min(1) @Max(15) Integer boletoDueDays,
        @NotNull @Min(3) @Max(10) Integer boletoAdvanceDays,
        @NotEmpty Set<OfferPaymentMethod> paymentMethods,
        @NotNull OfferPayoutDelay payoutDelay,
        @Null(message = "Produto é somente leitura") @Schema(accessMode = Schema.AccessMode.READ_ONLY) UUID productId,
        @Null(message = "Segmento é somente leitura") @Schema(accessMode = Schema.AccessMode.READ_ONLY) Segment segment,
        @Null(message = "Tipo de cobrança é somente leitura") @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        ChargeType chargeType,
        @Null(message = "Slug é somente leitura") @Schema(accessMode = Schema.AccessMode.READ_ONLY) String slug,
        @Null(message = "Status é somente leitura") @Schema(accessMode = Schema.AccessMode.READ_ONLY) String status
) {
    public OfferValues toValues() {
        return new OfferValues(priceCents, cycle, trialDays, trialRequiresCard, guaranteeDays,
                maxInstallments, boletoDueDays, boletoAdvanceDays, paymentMethods, payoutDelay);
    }
}
