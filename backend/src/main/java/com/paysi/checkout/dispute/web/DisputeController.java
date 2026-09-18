package com.paysi.checkout.dispute.web;

import com.paysi.checkout.dispute.app.DisputeCommand;
import com.paysi.checkout.dispute.app.DisputeOutcome;
import com.paysi.checkout.dispute.app.DisputeResult;
import com.paysi.checkout.dispute.app.DisputeService;
import com.paysi.checkout.dispute.app.EvidencePackage;
import com.paysi.identity.session.app.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** BE-12.2: contestação (chargeback) de uma cobrança, defesa rastreável e resolução, sempre pelo vendedor dono. */
@RestController
@Tag(name = "Cobranças")
public class DisputeController {
    private static final String SESSION_COOKIE = "paysi_session";

    private final DisputeService disputes;
    private final SessionService sessions;

    public DisputeController(DisputeService disputes, SessionService sessions) {
        this.disputes = disputes;
        this.sessions = sessions;
    }

    @PostMapping("/v1/charges/{chargeId}/disputes")
    @Operation(summary = "Abrir contestação (chargeback) de uma cobrança")
    public ResponseEntity<DisputeResponse> open(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable UUID chargeId,
            @RequestBody OpenDisputeRequest request) {
        UUID sellerId = sessions.authenticate(token).session().accountId();
        var result = disputes.open(sellerId, chargeId, new DisputeCommand(request.providerDisputeId(),
                request.reason(), request.amountCents(), request.acquirerFeeCents(), request.deadlineAt()));
        HttpStatus status = result.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(DisputeResponse.from(result));
    }

    @PostMapping("/v1/disputes/{disputeId}/resolve")
    @Operation(summary = "Registrar o resultado de uma contestação (ganha ou perdida)")
    public ResponseEntity<DisputeResponse> resolve(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable UUID disputeId,
            @RequestBody ResolveDisputeRequest request) {
        UUID sellerId = sessions.authenticate(token).session().accountId();
        var result = disputes.resolve(sellerId, disputeId, request.outcome(), request.memo());
        return ResponseEntity.ok(DisputeResponse.from(result));
    }

    @GetMapping("/v1/disputes/{disputeId}/evidence-package")
    @Operation(summary = "Montar o pacote de defesa reproduzível de uma contestação (RF-075)")
    public ResponseEntity<EvidencePackage> evidencePackage(
            @CookieValue(name = SESSION_COOKIE, required = false) String token,
            @PathVariable UUID disputeId) {
        UUID sellerId = sessions.authenticate(token).session().accountId();
        return ResponseEntity.ok(disputes.evidencePackage(sellerId, disputeId));
    }

    public record OpenDisputeRequest(String providerDisputeId, String reason, Long amountCents,
                                      Long acquirerFeeCents, Instant deadlineAt) {
    }

    public record ResolveDisputeRequest(DisputeOutcome outcome, String memo) {
    }

    public record DisputeResponse(UUID disputeId, UUID chargeId, String status, long sellerCents,
                                   long affiliateCents, Instant deadlineAt, boolean idempotentReplay) {
        static DisputeResponse from(DisputeResult result) {
            return new DisputeResponse(result.disputeId(), result.chargeId(), result.status(), result.sellerCents(),
                    result.affiliateCents(), result.deadlineAt(), result.idempotentReplay());
        }
    }
}
