package com.paysi.catalog.product.web.dto;

import com.paysi.catalog.product.app.ProductCommand;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public record ProductRequest(
        @NotBlank(message = "Nome do produto é obrigatório")
        @Schema(example = "CRM Pro", maxLength = 120)
        String name,

        @Schema(example = "Automação comercial para pequenas empresas", maxLength = 2000,
                nullable = true)
        String description,

        @NotNull(message = "Segmento é obrigatório")
        Segment segment,

        @NotNull(message = "Tipo de cobrança é obrigatório")
        ChargeType chargeType,

        @NotNull(message = "Configuração de afiliação é obrigatória")
        Boolean affiliationEnabled,

        @Null(message = "Status é somente leitura")
        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        String status
) {
    public ProductCommand toCommand() {
        return new ProductCommand(name, description, segment, chargeType,
                Boolean.TRUE.equals(affiliationEnabled));
    }
}
