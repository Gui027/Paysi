package com.paysi.checkout.pub.web;

import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.pub.app.CheckoutContract;
import com.paysi.checkout.pub.app.CheckoutContractService;
import com.paysi.core.error.NotFoundException;
import com.paysi.identity.domain.PersonType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CheckoutController.class)
class CheckoutControllerTest {
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CheckoutContractService contracts;

    @Test
    void exposesPublicContractWithoutInternalIdsAndWithCacheHeader() throws Exception {
        when(contracts.get("crm-pro-12345678")).thenReturn(contract());

        mvc.perform(get("/v1/offers/{slug}/checkout", "crm-pro-12345678"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=30, public"))
                .andExpect(jsonPath("$.product").value("Gestão Ágil"))
                .andExpect(jsonPath("$.segment").value("SAAS"))
                .andExpect(jsonPath("$.priceCents").value(10_000))
                .andExpect(jsonPath("$.requiredBuyerFields.PF[0]").value("name"))
                .andExpect(jsonPath("$.appearance.logoUrl").value("http://localhost:8080/v1/assets/x/content"))
                .andExpect(jsonPath("$.legalTexts.termsUrl").value("https://paysi.com.br/termos"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.productId").doesNotExist());
    }

    @Test
    void returnsNotFoundForUnpublishedOffer() throws Exception {
        when(contracts.get("rascunho")).thenThrow(new NotFoundException("OFFER_NOT_FOUND", "Oferta não encontrada"));

        mvc.perform(get("/v1/offers/{slug}/checkout", "rascunho"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));
    }

    private static CheckoutContract contract() {
        return new CheckoutContract("Gestão Ágil", Segment.SAAS, ChargeType.SUBSCRIPTION, 10_000,
                BillingCycle.MONTHLY, NOW, NOW.plusSeconds(30L * 86_400),
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD), 12,
                Map.of(PersonType.PF, List.of("name", "email", "personType", "taxId"),
                        PersonType.PJ, List.of("name", "email", "personType", "taxId", "legalName")),
                new CheckoutContract.Appearance("http://localhost:8080/v1/assets/x/content", null, null,
                        "#2563EB", "Comprar agora"),
                new CheckoutContract.LegalTexts("https://paysi.com.br/termos", "https://paysi.com.br/privacidade"));
    }
}
