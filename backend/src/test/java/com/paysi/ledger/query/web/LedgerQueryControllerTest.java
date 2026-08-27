package com.paysi.ledger.query.web;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.*;
import com.paysi.ledger.query.app.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;

class LedgerQueryControllerTest {
    @Test void alwaysUsesAccountFromAuthenticatedSession(){
        UUID account=UUID.randomUUID();SessionService sessions=mock(SessionService.class);LedgerQueryService ledger=mock(LedgerQueryService.class);
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",new SessionView(account,InitialMode.SELLER,Instant.now(),Instant.now().plusSeconds(60))));
        new LedgerQueryController(ledger,sessions).balance("cookie");
        verify(ledger).balance(account);
    }
}
