package com.paysi.apikey.web;

import com.paysi.affiliate.app.AffiliationService;
import com.paysi.apikey.app.ApiAuthService;
import com.paysi.apikey.app.ApiKeyModels;
import com.paysi.catalog.offer.app.OfferPublication;
import com.paysi.catalog.offer.app.OfferPublicationService;
import com.paysi.catalog.offer.app.OfferService;
import com.paysi.catalog.offer.app.OfferView;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.coupon.app.CouponService;
import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
import com.paysi.catalog.product.app.ProductCommand;
import com.paysi.catalog.product.app.ProductService;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Product;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.checkout.refund.app.RefundService;
import com.paysi.finance.app.FinanceService;
import com.paysi.reports.app.ReportsService;
import com.paysi.sales.app.SalesService;
import com.paysi.webhook.app.WebhookEndpointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicApiController.class)
class PublicCatalogControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean private ApiAuthService auth;
    @MockitoBean private ProductService products;
    @MockitoBean private OfferService offers;
    @MockitoBean private OfferPublicationService publications;
    @MockitoBean private SalesService sales;
    @MockitoBean private RefundService refunds;
    @MockitoBean private AffiliationService affiliations;
    @MockitoBean private FinanceService finance;
    @MockitoBean private ReportsService reports;
    @MockitoBean private WebhookEndpointService webhooks;
    @MockitoBean private CouponService coupons;

    @BeforeEach
    void authenticate() {
        when(auth.authenticate(any(), any(), any())).thenReturn(new ApiKeyModels.ApiPrincipal(
                SELLER, UUID.randomUUID(), java.util.List.of(ApiKeyModels.PRODUCTS, ApiKeyModels.PRODUCTS_WRITE)));
    }

    @Test
    void createsProductWithCatalogWriteScope() throws Exception {
        Product created = Product.createDraft(PRODUCT_ID, SELLER, "CRM Pro", "CRM", Segment.SAAS,
                ChargeType.SUBSCRIPTION, false, NOW);
        when(products.create(eq(SELLER), any(ProductCommand.class))).thenReturn(created);

        mvc.perform(post("/v1/public/products")
                        .header("Authorization", "Bearer paysi_live_key")
                        .contentType(MediaType.APPLICATION_JSON).content(productBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/public/products/" + PRODUCT_ID))
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(auth).authenticate("Bearer paysi_live_key", null, ApiKeyModels.PRODUCTS_WRITE);
        verify(products).create(eq(SELLER), any(ProductCommand.class));
    }

    @Test
    void createsAndPublishesOfferWithCatalogWriteScope() throws Exception {
        OfferView draft = offer(OfferStatus.DRAFT);
        when(offers.create(eq(SELLER), eq(PRODUCT_ID), any())).thenReturn(draft);
        when(publications.publish(SELLER, draft.offer().id())).thenReturn(OfferPublication.published(
                offer(OfferStatus.PUBLISHED, draft.offer().id())));

        mvc.perform(post("/v1/public/products/{productId}/offers", PRODUCT_ID)
                        .header("Authorization", "Bearer paysi_live_key")
                        .contentType(MediaType.APPLICATION_JSON).content(offerBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/public/offers/" + draft.offer().id()))
                .andExpect(jsonPath("$.slug").value(draft.offer().slug()));

        mvc.perform(post("/v1/public/offers/{offerId}/publish", draft.offer().id())
                        .header("Authorization", "Bearer paysi_live_key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.offer.status").value("PUBLISHED"));

        verify(auth, org.mockito.Mockito.times(2)).authenticate(
                "Bearer paysi_live_key", null, ApiKeyModels.PRODUCTS_WRITE);
    }

    @Test
    void validatesReadOnlyProductFieldsBeforeCreating() throws Exception {
        mvc.perform(post("/v1/public/products")
                        .header("Authorization", "Bearer paysi_live_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productBody().replace("\"affiliationEnabled\": false",
                                "\"affiliationEnabled\": false, \"status\": \"ACTIVE\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(products);
    }

    @Test
    void managesCouponsWithCatalogScopes() throws Exception {
        UUID couponId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        Coupon coupon = new Coupon(couponId, SELLER, "PROMO10", CouponKind.PERCENT, 1_000,
                null, null, 50, 1, 0, Set.of(offerId), null, NOW);
        when(coupons.list(SELLER)).thenReturn(java.util.List.of(coupon));
        when(coupons.create(eq(SELLER), eq("PROMO10"), any())).thenReturn(coupon);

        mvc.perform(get("/v1/public/coupons").header("Authorization", "Bearer paysi_live_key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("PROMO10"));
        mvc.perform(post("/v1/public/coupons").header("Authorization", "Bearer paysi_live_key")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"code":"PROMO10","discountType":"PERCENT","discountBps":1000,
                                 "maxRedemptions":50,"maxPerBuyer":1,"offerIds":["%s"]}
                                """.formatted(offerId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/public/coupons/" + couponId));
        mvc.perform(delete("/v1/public/coupons/{id}", couponId)
                        .header("Authorization", "Bearer paysi_live_key"))
                .andExpect(status().isNoContent());

        verify(auth).authenticate("Bearer paysi_live_key", null, ApiKeyModels.PRODUCTS);
        verify(auth, org.mockito.Mockito.times(2)).authenticate(
                "Bearer paysi_live_key", null, ApiKeyModels.PRODUCTS_WRITE);
        verify(coupons).archive(SELLER, couponId);
    }

    private static String productBody() {
        return """
                {
                  "name": "CRM Pro",
                  "description": "CRM",
                  "segment": "SAAS",
                  "chargeType": "SUBSCRIPTION",
                  "affiliationEnabled": false
                }
                """;
    }

    private static String offerBody() {
        return """
                {
                  "priceCents": 4990,
                  "cycle": "MONTHLY",
                  "trialDays": 0,
                  "trialRequiresCard": false,
                  "guaranteeDays": 7,
                  "maxInstallments": 1,
                  "boletoDueDays": 3,
                  "boletoAdvanceDays": 5,
                  "paymentMethods": ["CARD"],
                  "payoutDelay": "D15",
                  "name": "CRM mensal"
                }
                """;
    }

    private static OfferView offer(OfferStatus status) {
        return offer(status, UUID.randomUUID());
    }

    private static OfferView offer(OfferStatus status, UUID offerId) {
        Offer offer = new Offer(offerId, PRODUCT_ID, ChargeType.SUBSCRIPTION, Segment.SAAS,
                "crm-pro-" + offerId.toString().substring(0, 8), 4_990, BillingCycle.MONTHLY,
                0, false, 7, 1, 3, 5, Set.of(OfferPaymentMethod.CARD), OfferPayoutDelay.D15,
                status, null, NOW, NOW);
        return new OfferView(offer, NOW.plusSeconds(15L * 86_400));
    }
}
