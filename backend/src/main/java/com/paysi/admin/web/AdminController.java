package com.paysi.admin.web;

import com.paysi.admin.app.AdminAuthService;
import com.paysi.admin.app.AdminService;
import com.paysi.admin.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {
    private final AdminAuthService auth;
    private final AdminService admin;

    public AdminController(AdminAuthService auth, AdminService admin) {
        this.auth = auth;
        this.admin = admin;
    }

    @GetMapping("/search")
    public List<AdminSearchResult> search(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                          @RequestParam String query) {
        auth.authenticate(authorization, totp, Set.of("SUPPORT", "RISK", "COMPLIANCE"));
        return admin.search(query);
    }

    @PatchMapping("/{type}/{id}/status")
    public ResponseEntity<Void> changeStatus(@RequestHeader(value = "Authorization", required = false) String authorization,
                                              @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                              @PathVariable String type, @PathVariable UUID id,
                                              @RequestBody StatusRequest request) {
        var actor = auth.authenticate(authorization, totp, Set.of("SUPPORT", "RISK"));
        admin.changeStatus(actor, type, id, request.status(), request.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/adjustments")
    public ResponseEntity<AdjustmentView> requestAdjustment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
            @RequestBody AdjustmentCommand command) {
        var actor = auth.authenticate(authorization, totp, Set.of("RISK"));
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.requestAdjustment(actor, command));
    }

    @PostMapping("/adjustments/{id}/approve")
    public AdjustmentView approveAdjustment(@RequestHeader(value = "Authorization", required = false) String authorization,
                                             @RequestHeader(value = "X-Admin-Totp", required = false) String totp,
                                             @PathVariable UUID id,
                                             @RequestBody ReasonRequest request) {
        var actor = auth.authenticate(authorization, totp, Set.of("RISK"));
        return admin.approveAdjustment(actor, id, request.reason());
    }

    public record StatusRequest(String status, String reason) {}
    public record ReasonRequest(String reason) {}
}
