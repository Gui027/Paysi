package com.paysi.affiliate.web;

import com.paysi.affiliate.app.AffiliationRole;
import com.paysi.affiliate.app.AffiliationService;
import com.paysi.affiliate.web.dto.AffiliationApprovalRequest;
import com.paysi.affiliate.web.dto.AffiliationEndRequest;
import com.paysi.affiliate.web.dto.AffiliationPageResponse;
import com.paysi.affiliate.web.dto.AffiliationRequest;
import com.paysi.affiliate.web.dto.AffiliationResponse;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/v1/affiliations")
@Tag(name = "Afiliações")
public class AffiliationController {
    private static final String COOKIE_NAME = "paysi_session";

    private final AffiliationService affiliations;
    private final SessionService sessions;

    public AffiliationController(AffiliationService affiliations, SessionService sessions) {
        this.affiliations = affiliations;
        this.sessions = sessions;
    }

    @PostMapping
    @Operation(summary = "Solicitar afiliação a um produto")
    public ResponseEntity<AffiliationResponse> request(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @Valid @RequestBody AffiliationRequest request) {
        var created = affiliations.request(accountId(token), request.productId());
        return ResponseEntity.created(URI.create("/v1/affiliations/" + created.id()))
                .body(AffiliationResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "Listar afiliações como afiliado ou vendedor")
    public AffiliationPageResponse list(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam(defaultValue = "AFFILIATE") AffiliationRole role,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return AffiliationPageResponse.from(affiliations.list(accountId(token), role, cursor, limit));
    }

    @PostMapping("/{affiliationId}/approve")
    @Operation(summary = "Aprovar afiliação e congelar a comissão")
    public AffiliationResponse approve(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID affiliationId,
            @Valid @RequestBody AffiliationApprovalRequest request) {
        return AffiliationResponse.from(affiliations.approve(accountId(token), affiliationId,
                request.commissionBps(), request.recurrence()));
    }

    @PostMapping("/{affiliationId}/end")
    @Operation(summary = "Encerrar afiliação com motivo explícito")
    public AffiliationResponse end(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @PathVariable UUID affiliationId,
            @Valid @RequestBody AffiliationEndRequest request) {
        return AffiliationResponse.from(affiliations.end(accountId(token), affiliationId, request.reason()));
    }

    private UUID accountId(String token) {
        return sessions.authenticate(token).session().accountId();
    }
}
