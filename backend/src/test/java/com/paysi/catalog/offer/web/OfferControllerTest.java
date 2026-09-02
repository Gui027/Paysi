package com.paysi.catalog.offer.web;

import com.paysi.catalog.offer.app.OfferService;
import com.paysi.catalog.offer.app.OfferView;
import com.paysi.catalog.offer.app.OfferPublication;
import com.paysi.catalog.offer.app.OfferPublicationService;
import com.paysi.catalog.offer.app.PublicationAction;
import com.paysi.catalog.offer.domain.BillingCycle;
import com.paysi.catalog.offer.domain.Offer;
import com.paysi.catalog.offer.domain.OfferPaymentMethod;
import com.paysi.catalog.offer.domain.OfferPayoutDelay;
import com.paysi.catalog.offer.domain.OfferStatus;
import com.paysi.catalog.product.domain.ChargeType;
import com.paysi.catalog.product.domain.Segment;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfferController.class)
class OfferControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OfferService offers;

    @MockitoBean
    private OfferPublicationService publications;

    @MockitoBean
    private SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(SELLER, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void createsOfferWithoutAcceptingInternalContractFields() throws Exception {
        OfferView created = view();
        when(offers.create(eq(SELLER), eq(PRODUCT), any())).thenReturn(created);

        mvc.perform(post("/v1/products/{id}/offers", PRODUCT).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/offers/" + created.offer().id()))
                .andExpect(jsonPath("$.slug").value(created.offer().slug()))
                .andExpect(jsonPath("$.segment").value("SAAS"))
                .andExpect(jsonPath("$.chargeType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.availableAt").value("2026-09-17T12:00:00Z"));

        mvc.perform(post("/v1/products/{id}/offers", PRODUCT).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"payoutDelay\": \"D15\"",
                                "\"payoutDelay\": \"D15\", \"slug\": \"forjado\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listsDetailsUpdatesAndArchivesUsingAuthenticatedSeller() throws Exception {
        OfferView stored = view();
        when(offers.list(SELLER, PRODUCT)).thenReturn(List.of(stored));
        when(offers.get(SELLER, stored.offer().id())).thenReturn(stored);
        when(offers.update(eq(SELLER), eq(stored.offer().id()), any())).thenReturn(stored);

        mvc.perform(get("/v1/products/{id}/offers", PRODUCT).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id")
                        .value(stored.offer().id().toString()));
        mvc.perform(get("/v1/offers/{id}", stored.offer().id()).cookie(cookie()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id")
                        .value(stored.offer().id().toString()));
        mvc.perform(put("/v1/offers/{id}", stored.offer().id()).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/offers/{id}", stored.offer().id()).cookie(cookie()))
                .andExpect(status().isNoContent());

        verify(offers).archive(SELLER, stored.offer().id());
    }

    @Test
    void publishesOfferOrReturnsRequiredKycAction() throws Exception {
        OfferView stored = view();
        when(publications.publish(SELLER, stored.offer().id()))
                .thenReturn(OfferPublication.action(PublicationAction.COMPLETE_KYC,
                        "https://kyc/process", stored));

        mvc.perform(post("/v1/offers/{id}/publish", stored.offer().id()).cookie(cookie()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.requiredAction").value("COMPLETE_KYC"))
                .andExpect(jsonPath("$.actionUrl").value("https://kyc/process"));

        Offer published = new Offer(stored.offer().id(), PRODUCT, ChargeType.SUBSCRIPTION, Segment.SAAS,
                stored.offer().slug(), 10_000, BillingCycle.MONTHLY, 7, false, 7, 12, 3, 5,
                stored.offer().paymentMethods(), OfferPayoutDelay.D15, OfferStatus.PUBLISHED,
                null, NOW, NOW);
        when(publications.publish(SELLER, stored.offer().id()))
                .thenReturn(OfferPublication.published(new OfferView(published, stored.availableAt())));

        mvc.perform(post("/v1/offers/{id}/publish", stored.offer().id()).cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.offer.status").value("PUBLISHED"));
    }

    @Test
    void exposesOnlyPublishedCheckoutContractWithoutSession() throws Exception {
        OfferView published = view();
        when(offers.getPublished(published.offer().slug())).thenReturn(published);

        mvc.perform(get("/v1/offers/{slug}/checkout", published.offer().slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(published.offer().slug()));

        verify(offers).getPublished(published.offer().slug());
    }

    @Test
    void rejectsInvalidRangesAndEnumsBeforeService() throws Exception {
        mvc.perform(post("/v1/products/{id}/offers", PRODUCT).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("10000", "1999")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/v1/products/{id}/offers", PRODUCT).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("MONTHLY", "INVALID")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ENUM"))
                .andExpect(jsonPath("$.field").value("cycle"));

        verifyNoInteractions(offers);
    }

    @Test
    void rejectsAttemptsToChangeImmutableContractFields() throws Exception {
        mvc.perform(put("/v1/offers/{id}", UUID.randomUUID()).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"payoutDelay\": \"D15\"",
                                "\"payoutDelay\": \"D15\", \"productId\": \"" + PRODUCT
                                        + "\", \"segment\": \"DIGITAL\", \"chargeType\": \"ONE_TIME\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("paysi_session", "cookie");
    }

    private static String validBody() {
        return """
                {
                  "priceCents": 10000,
                  "cycle": "MONTHLY",
                  "trialDays": 7,
                  "trialRequiresCard": false,
                  "guaranteeDays": 7,
                  "maxInstallments": 12,
                  "boletoDueDays": 3,
                  "boletoAdvanceDays": 5,
                  "paymentMethods": ["PIX", "CARD", "BOLETO"],
                  "payoutDelay": "D15"
                }
                """;
    }

    private static OfferView view() {
        Offer offer = new Offer(UUID.randomUUID(), PRODUCT, ChargeType.SUBSCRIPTION, Segment.SAAS,
                "crm-pro-12345678", 10_000, BillingCycle.MONTHLY, 7, false, 7, 12, 3, 5,
                Set.of(OfferPaymentMethod.PIX, OfferPaymentMethod.CARD, OfferPaymentMethod.BOLETO),
                OfferPayoutDelay.D15, OfferStatus.DRAFT, null, NOW, NOW);
        return new OfferView(offer, NOW.plusSeconds(15L * 86_400));
    }
}
