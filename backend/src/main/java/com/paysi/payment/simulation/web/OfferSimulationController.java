package com.paysi.payment.simulation.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.payment.simulation.app.OfferSimulationService;
import com.paysi.payment.simulation.web.dto.OfferSimulationRequest;
import com.paysi.payment.simulation.web.dto.OfferSimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/offers")
@Tag(name = "Ofertas")
public class OfferSimulationController {
    private static final String COOKIE_NAME = "paysi_session";

    private final OfferSimulationService simulations;
    private final SessionService sessions;

    public OfferSimulationController(OfferSimulationService simulations, SessionService sessions) {
        this.simulations = simulations;
        this.sessions = sessions;
    }

    @PostMapping("/{offerId}/simulation")
    @Operation(summary = "Simular preço e divisão da oferta")
    public OfferSimulationResponse simulate(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId,
            @Valid @RequestBody OfferSimulationRequest request) {
        UUID sellerId = sessions.authenticate(token).session().accountId();
        return OfferSimulationResponse.from(simulations.simulate(sellerId, offerId, request));
    }
}
