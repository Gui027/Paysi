package com.paysi.identity.kyc.web;

import com.paysi.identity.kyc.app.KycService;
import com.paysi.identity.kyc.app.KycView;
import com.paysi.identity.session.app.SessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/accounts/me")
public class KycController {
    private static final String COOKIE_NAME = "paysi_session";
    private final KycService kyc;
    private final SessionService sessions;
    public KycController(KycService kyc, SessionService sessions) { this.kyc = kyc; this.sessions = sessions; }

    @GetMapping
    public KycView current(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return kyc.current(sessions.authenticate(token).session().accountId());
    }

    @PostMapping("/kyc")
    public KycView start(@CookieValue(name = COOKIE_NAME, required = false) String token) {
        return kyc.start(sessions.authenticate(token).session().accountId());
    }
}
