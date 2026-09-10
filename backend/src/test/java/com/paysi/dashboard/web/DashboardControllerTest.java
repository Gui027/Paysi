package com.paysi.dashboard.web;

import com.paysi.dashboard.app.DashboardService;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {
    @Test
    void alwaysUsesAuthenticatedAccountAndPassesPeriod() {
        UUID account = UUID.randomUUID();
        SessionService sessions = mock(SessionService.class);
        DashboardService dashboard = mock(DashboardService.class);
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(account, InitialMode.SELLER, Instant.now(), Instant.now().plusSeconds(60))));

        new DashboardController(dashboard, sessions).current("cookie", "30d");

        verify(dashboard).get(account, "30d");
    }
}
