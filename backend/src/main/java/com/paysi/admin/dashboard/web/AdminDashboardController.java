package com.paysi.admin.dashboard.web;

import com.paysi.admin.dashboard.app.AdminDashboardAuthService;
import com.paysi.admin.dashboard.app.AdminDashboardService;
import com.paysi.admin.dashboard.domain.AdminDashboardView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/admin-dashboard")
public class AdminDashboardController {
    private final AdminDashboardAuthService auth;
    private final AdminDashboardService dashboard;

    public AdminDashboardController(AdminDashboardAuthService auth, AdminDashboardService dashboard) {
        this.auth = auth;
        this.dashboard = dashboard;
    }

    @GetMapping
    public ResponseEntity<AdminDashboardView> dashboard(
            @CookieValue(name = AdminDashboardSessionController.COOKIE, required = false) String token,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        auth.authenticate(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dashboard.dashboard(days, q, page, size));
    }
}
