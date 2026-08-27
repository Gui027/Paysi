package com.paysi.ledger.query.web;

import com.paysi.identity.session.app.SessionService;
import com.paysi.ledger.query.app.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/v1/accounts/me")
public class LedgerQueryController {
    private static final String COOKIE_NAME="paysi_session";private final LedgerQueryService ledger;private final SessionService sessions;
    public LedgerQueryController(LedgerQueryService ledger,SessionService sessions){this.ledger=ledger;this.sessions=sessions;}
    @GetMapping("/balance") public BalanceView balance(@CookieValue(name=COOKIE_NAME,required=false)String token){return ledger.balance(sessions.authenticate(token).session().accountId());}
    @GetMapping("/ledger") public LedgerPage entries(@CookieValue(name=COOKIE_NAME,required=false)String token,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int limit){return ledger.entries(sessions.authenticate(token).session().accountId(),cursor,limit);}
}
