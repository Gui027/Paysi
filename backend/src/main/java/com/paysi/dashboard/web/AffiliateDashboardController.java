package com.paysi.dashboard.web;

import com.paysi.dashboard.app.AffiliateDashboardService;
import com.paysi.dashboard.app.AffiliateDashboardView;
import com.paysi.identity.session.app.SessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts/me/dashboard/affiliate")
public class AffiliateDashboardController {
    private static final String COOKIE_NAME = "paysi_session";
    private final AffiliateDashboardService dashboard;
    private final SessionService sessions;

    public AffiliateDashboardController(AffiliateDashboardService dashboard, SessionService sessions) {
        this.dashboard = dashboard;
        this.sessions = sessions;
    }

    @GetMapping
    public AffiliateDashboardView current(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                          @RequestParam(defaultValue = "today") String period) {
        return dashboard.get(sessions.authenticate(token).session().accountId(), period);
    }
}
