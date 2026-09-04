package com.paysi.catalog.appearance.web;

import com.paysi.catalog.appearance.app.AppearanceService;
import com.paysi.catalog.appearance.web.dto.AppearanceRequest;
import com.paysi.catalog.appearance.web.dto.AppearanceResponse;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/offers/{offerId}/appearance")
@Tag(name = "Aparência do checkout")
public class AppearanceController {
    private static final String COOKIE_NAME = "paysi_session";

    private final AppearanceService appearances;
    private final SessionService sessions;

    public AppearanceController(AppearanceService appearances, SessionService sessions) {
        this.appearances = appearances;
        this.sessions = sessions;
    }

    @GetMapping
    @Operation(summary = "Consultar aparência da oferta")
    public AppearanceResponse get(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId) {
        return AppearanceResponse.from(appearances.get(accountId(token), offerId));
    }

    @PutMapping
    @Operation(summary = "Configurar aparência segura da oferta")
    public AppearanceResponse update(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID offerId, @Valid @RequestBody AppearanceRequest request) {
        return AppearanceResponse.from(appearances.update(accountId(token), offerId, request.toCommand()));
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
