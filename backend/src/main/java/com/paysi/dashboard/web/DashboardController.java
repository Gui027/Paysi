package com.paysi.dashboard.web;

import com.paysi.dashboard.app.DashboardService;
import com.paysi.dashboard.app.DashboardView;
import com.paysi.identity.session.app.SessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts/me/dashboard")
public class DashboardController {
    private static final String COOKIE_NAME = "paysi_session";
    private final DashboardService dashboard;
    private final SessionService sessions;

    public DashboardController(DashboardService dashboard, SessionService sessions) {
        this.dashboard = dashboard;
        this.sessions = sessions;
    }

    @GetMapping
    public DashboardView current(
            @CookieValue(name = COOKIE_NAME, required = false) String token,
            @RequestParam(defaultValue = "today") String period) {
        return dashboard.get(sessions.authenticate(token).session().accountId(), period);
    }
}
