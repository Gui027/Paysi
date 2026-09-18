package com.paysi.compliance.lgpd.web;

import com.paysi.admin.app.AdminAuthService;
import com.paysi.compliance.lgpd.app.CreateLgpdRequestCommand;
import com.paysi.compliance.lgpd.app.LgpdRequestService;
import com.paysi.compliance.lgpd.app.LgpdRequestView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/** BE-14.4: rastreio de pedido de titular sob a LGPD — fluxo interno, papel COMPLIANCE. */
@RestController
@RequestMapping("/v1/admin/lgpd-requests")
public class LgpdRequestController {
    private final AdminAuthService auth;
    private final LgpdRequestService requests;

    public LgpdRequestController(AdminAuthService auth, LgpdRequestService requests) {
        this.auth = auth;
        this.requests = requests;
    }

    @PostMapping
    public ResponseEntity<LgpdRequestView> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
            @RequestBody CreateLgpdRequestCommand command) {
        auth.authenticate(authorization, totp, Set.of("COMPLIANCE"));
        return ResponseEntity.status(HttpStatus.CREATED).body(requests.create(command));
    }

    @PostMapping("/{id}/assign")
    public LgpdRequestView assign(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                   @PathVariable UUID id, @RequestBody AssignRequest request) {
        auth.authenticate(authorization, totp, Set.of("COMPLIANCE"));
        return requests.assign(id, request.assigneeId());
    }

    @PostMapping("/{id}/resolve")
    public LgpdRequestView resolve(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                    @PathVariable UUID id, @RequestBody ResolveRequest request) {
        auth.authenticate(authorization, totp, Set.of("COMPLIANCE"));
        return requests.resolve(id, request.status(), request.evidence());
    }

    public record AssignRequest(UUID assigneeId) {
    }

    public record ResolveRequest(String status, String evidence) {
    }
}
