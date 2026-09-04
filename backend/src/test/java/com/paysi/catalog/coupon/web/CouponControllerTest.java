package com.paysi.catalog.coupon.web;

import com.paysi.catalog.coupon.app.CouponService;
import com.paysi.catalog.coupon.domain.Coupon;
import com.paysi.catalog.coupon.domain.CouponKind;
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

@WebMvcTest(CouponController.class)
class CouponControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OFFER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CouponService coupons;

    @MockitoBean
    private SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(SELLER, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void createsCouponAndRejectsBothDiscountFields() throws Exception {
        Coupon created = coupon();
        when(coupons.create(eq(SELLER), eq("promo10"), any())).thenReturn(created);

        mvc.perform(post("/v1/coupons").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/coupons/" + created.id()))
                .andExpect(jsonPath("$.code").value("PROMO10"))
                .andExpect(jsonPath("$.discountType").value("PERCENT"))
                .andExpect(jsonPath("$.discountBps").value(1000));

        mvc.perform(post("/v1/coupons").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"discountBps\": 1000",
                                "\"discountBps\": 1000, \"discountCents\": 500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COUPON_INVALID"))
                .andExpect(jsonPath("$.field").value("discountCents"));
    }

    @Test
    void listsDetailsUpdatesAndArchivesUsingAuthenticatedSeller() throws Exception {
        Coupon stored = coupon();
        when(coupons.list(SELLER)).thenReturn(List.of(stored));
        when(coupons.get(SELLER, stored.id())).thenReturn(stored);
        when(coupons.update(eq(SELLER), eq(stored.id()), any())).thenReturn(stored);

        mvc.perform(get("/v1/coupons").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stored.id().toString()));
        mvc.perform(get("/v1/coupons/{id}", stored.id()).cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(stored.id().toString()));
        mvc.perform(put("/v1/coupons/{id}", stored.id()).cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk());
        mvc.perform(delete("/v1/coupons/{id}", stored.id()).cookie(cookie()))
                .andExpect(status().isNoContent());

        verify(coupons).archive(SELLER, stored.id());
    }

    @Test
    void rejectsMissingRequiredFieldsBeforeService() throws Exception {
        mvc.perform(post("/v1/coupons").cookie(cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody().replace("\"offerIds\": [\"" + OFFER + "\"]", "\"offerIds\": []")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(coupons);
    }

    private static jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("paysi_session", "cookie");
    }

    private static String validBody() {
        return """
                {
                  "code": "promo10",
                  "discountType": "PERCENT",
                  "discountBps": 1000,
                  "maxPerBuyer": 1,
                  "offerIds": ["%s"]
                }
                """.formatted(OFFER);
    }

    private static Coupon coupon() {
        return new Coupon(UUID.randomUUID(), SELLER, "PROMO10", CouponKind.PERCENT, 1_000, null, null,
                null, 1, 0, Set.of(OFFER), null, NOW);
    }
}
