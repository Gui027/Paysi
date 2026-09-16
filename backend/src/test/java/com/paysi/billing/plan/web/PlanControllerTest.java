package com.paysi.billing.plan.web;

import com.paysi.billing.plan.app.PlanService;
import com.paysi.billing.plan.domain.PlanStatus;
import com.paysi.billing.plan.domain.PlatformSubscription;
import com.paysi.billing.plan.port.PlanRepository.PlanChangeRecord;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import com.paysi.payment.split.Plan;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlanController.class)
class PlanControllerTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean PlanService plans;
    @MockitoBean SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(ACCOUNT, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void returnsCurrentPlanWithPriceTableAndNoPendingChange() throws Exception {
        when(plans.get(ACCOUNT)).thenReturn(subscription(Plan.TRANSACIONAL, null, null, null));

        mvc.perform(get("/v1/accounts/me/plan").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPlan").value("TRANSACIONAL"))
                .andExpect(jsonPath("$.monthlyFee").value(0))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.pendingPlan").doesNotExist())
                .andExpect(jsonPath("$.priceTable.TRANSACIONAL").value(0))
                .andExpect(jsonPath("$.priceTable.ESCALA").value(19_900));
    }

    @Test
    void showsPendingChangeWithEffectiveDate() throws Exception {
        Instant effective = NOW.plusSeconds(30L * 86_400);
        when(plans.get(ACCOUNT)).thenReturn(subscription(Plan.TRANSACIONAL, Plan.ESCALA, 19_900L, effective));

        mvc.perform(get("/v1/accounts/me/plan").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPlan").value("ESCALA"))
                .andExpect(jsonPath("$.pendingMonthlyFee").value(19_900))
                .andExpect(jsonPath("$.pendingEffectiveAt").exists());
    }

    @Test
    void requestsChangeWithCardToken() throws Exception {
        when(plans.requestChange(ACCOUNT, Plan.ESCALA, "tok_1"))
                .thenReturn(subscription(Plan.TRANSACIONAL, Plan.ESCALA, 19_900L, NOW.plusSeconds(86_400)));

        mvc.perform(post("/v1/accounts/me/plan/change").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"ESCALA\",\"cardToken\":\"tok_1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingPlan").value("ESCALA"));
    }

    @Test
    void rejectsInvalidPlanName() throws Exception {
        mvc.perform(post("/v1/accounts/me/plan/change").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PREMIUM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_INVALID"));
    }

    @Test
    void listsHistoryWithDefaultLimit() throws Exception {
        when(plans.history(ACCOUNT, 20)).thenReturn(List.of(
                new PlanChangeRecord(UUID.randomUUID(), "TRANSACIONAL", "ESCALA", "v1-provisional", NOW)));

        mvc.perform(get("/v1/accounts/me/plan/history").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fromPlan").value("TRANSACIONAL"))
                .andExpect(jsonPath("$[0].toPlan").value("ESCALA"));
    }

    private static PlatformSubscription subscription(Plan plan, Plan pendingPlan, Long pendingPrice,
                                                       Instant pendingEffectiveAt) {
        return new PlatformSubscription(ACCOUNT, plan, plan == Plan.ESCALA ? 19_900 : 0,
                NOW.minusSeconds(86_400), NOW.plusSeconds(29L * 86_400), PlanStatus.ACTIVE, null,
                pendingPlan, pendingPrice, pendingEffectiveAt, null);
    }

    private static jakarta.servlet.http.Cookie cookie() {
        return new jakarta.servlet.http.Cookie("paysi_session", "cookie");
    }
}
