package com.paysi.affiliate.web;

import com.paysi.affiliate.app.AffiliateProgramService;
import com.paysi.affiliate.web.dto.AffiliateProgramRequest;
import com.paysi.affiliate.web.dto.AffiliateProgramResponse;
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
@RequestMapping("/v1/products/{productId}/affiliate-program")
@Tag(name = "Afiliações")
public class AffiliateProgramController {
    private static final String COOKIE_NAME = "paysi_session";

    private final AffiliateProgramService programs;
    private final SessionService sessions;

    public AffiliateProgramController(AffiliateProgramService programs, SessionService sessions) {
        this.programs = programs;
        this.sessions = sessions;
    }

    @GetMapping
    @Operation(summary = "Configuração do programa de afiliados do produto")
    public AffiliateProgramResponse get(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId) {
        return AffiliateProgramResponse.from(programs.get(accountId(token), productId));
    }

    @PutMapping
    @Operation(summary = "Salvar a configuração do programa de afiliados do produto")
    public AffiliateProgramResponse update(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID productId,
            @Valid @RequestBody AffiliateProgramRequest request) {
        return AffiliateProgramResponse.from(programs.update(accountId(token), productId,
                request.commissionBps(), request.recurrence(), request.autoApprove(),
                request.supportEmail(), request.description()));
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
