package com.paysi.affiliate.web;

import com.paysi.affiliate.app.AffiliationPage;
import com.paysi.affiliate.app.AffiliationRole;
import com.paysi.affiliate.app.AffiliationService;
import com.paysi.affiliate.app.MarketplaceItem;
import com.paysi.affiliate.app.MarketplacePage;
import com.paysi.affiliate.app.MarketplaceService;
import com.paysi.affiliate.domain.Affiliation;
import com.paysi.affiliate.domain.AffiliationRecurrence;
import com.paysi.affiliate.domain.AffiliationStatus;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({MarketplaceController.class, AffiliationController.class})
class AffiliateControllerTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID AFFILIATION = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean MarketplaceService marketplace;
    @MockitoBean AffiliationService affiliations;
    @MockitoBean SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(ACCOUNT, InitialMode.AFFILIATE, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void marketplaceIsPublicAndDoesNotExposeInternalSellerId() throws Exception {
        when(marketplace.list(null, 20)).thenReturn(new MarketplacePage(List.of(new MarketplaceItem(
                PRODUCT, SELLER, "Curso", "Descrição", "Vendedor", Segment.DIGITAL,
                ChargeType.ONE_TIME, 10_000, 1_500, 7, 32, 60, NOW)), null));

        mvc.perform(get("/v1/marketplace").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.items[0].startingPriceCents").value(10_000))
                .andExpect(jsonPath("$.items[0].suggestedCommissionBps").value(1_500))
                .andExpect(jsonPath("$.items[0].payoutDelayDays").value(32))
                .andExpect(jsonPath("$.items[0].attributionDays").value(60))
                .andExpect(jsonPath("$.items[0].sellerId").doesNotExist());
    }

    @Test
    void requestsAffiliationUsingAuthenticatedAccount() throws Exception {
        Affiliation affiliation = affiliation(AffiliationStatus.PENDING);
        when(affiliations.request(ACCOUNT, PRODUCT)).thenReturn(affiliation);

        mvc.perform(post("/v1/affiliations").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + PRODUCT + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/affiliations/" + AFFILIATION))
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(affiliations).request(ACCOUNT, PRODUCT);
    }

    @Test
    void listsSellerQueueAndApprovesWithExplicitRecurrence() throws Exception {
        Affiliation affiliation = affiliation(AffiliationStatus.APPROVED);
        when(affiliations.list(ACCOUNT, AffiliationRole.SELLER, null, 10))
                .thenReturn(new AffiliationPage(List.of(affiliation), null));
        when(affiliations.approve(ACCOUNT, AFFILIATION, 1_000, AffiliationRecurrence.ALL_CYCLES))
                .thenReturn(affiliation);

        mvc.perform(get("/v1/affiliations").cookie(cookie()).param("role", "SELLER").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("APPROVED"));
        mvc.perform(post("/v1/affiliations/{id}/approve", AFFILIATION).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionBps\":1000,\"recurrence\":\"ALL_CYCLES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commissionBps").value(1_000))
                .andExpect(jsonPath("$.recurrence").value("ALL_CYCLES"));
    }

    @Test
    void validatesRequestAndApprovalBody() throws Exception {
        mvc.perform(post("/v1/affiliations").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("productId"));

        mvc.perform(post("/v1/affiliations/{id}/approve", AFFILIATION).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commissionBps\":5001,\"recurrence\":\"FIRST_CHARGE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("commissionBps"));
    }

    private static jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("paysi_session", "cookie");
    }

    private static Affiliation affiliation(AffiliationStatus status) {
        return new Affiliation(AFFILIATION, PRODUCT, "Curso", SELLER, "Vendedor", ACCOUNT, "Afiliado",
                status == AffiliationStatus.PENDING ? 0 : 1_000, AffiliationRecurrence.ALL_CYCLES,
                status, null, status == AffiliationStatus.PENDING ? null : NOW, null, NOW);
    }
}
