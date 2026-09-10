package com.paysi.payment.simulation.web;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import com.paysi.payment.simulation.app.OfferSimulationService;
import com.paysi.payment.simulation.app.OfferSimulationService.OfferSimulation;
import com.paysi.payment.simulation.web.dto.OfferSimulationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfferSimulationController.class)
class OfferSimulationControllerTest {
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID OFFER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Autowired private MockMvc mvc;
    @MockitoBean private OfferSimulationService simulations;
    @MockitoBean private SessionService sessions;

    @BeforeEach
    void authenticate() {
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(SELLER, InitialMode.SELLER, NOW, NOW.plusSeconds(3600))));
    }

    @Test
    void authenticatesSellerAndReturnsServerValues() throws Exception {
        when(simulations.simulate(eq(SELLER), eq(OFFER), org.mockito.ArgumentMatchers.any(OfferSimulationRequest.class)))
                .thenReturn(new OfferSimulation(10_000, 0, 10_000, 399, 199, 0, 9_601,
                        Instant.parse("2026-09-16T12:00:00Z")));

        mvc.perform(post("/v1/offers/{id}/simulation", OFFER).cookie(new jakarta.servlet.http.Cookie("paysi_session", "cookie"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"PIX\",\"installments\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidCents").value(10_000))
                .andExpect(jsonPath("$.commissionCents").value(0));
        verify(simulations).simulate(eq(SELLER), eq(OFFER), org.mockito.ArgumentMatchers.any(OfferSimulationRequest.class));
    }
}
