package com.paysi.identity.kyc.web;

import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.kyc.app.KycService;
import com.paysi.identity.kyc.app.KycView;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KycControllerTest {
    @Test
    void derivesAccountFromSessionInsteadOfRequestInput() {
        UUID accountId = UUID.randomUUID();
        SessionService sessions = mock(SessionService.class);
        KycService kyc = mock(KycService.class);
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(accountId, InitialMode.SELLER, Instant.now(), Instant.now().plusSeconds(60))));
        when(kyc.start(accountId)).thenReturn(new KycView(accountId, KycStatus.SUBMITTED, "https://provider/process", List.of()));

        var response = new KycController(kyc, sessions).start("cookie");

        assertThat(response.accountId()).isEqualTo(accountId);
        verify(kyc).start(accountId);
    }

    @Test
    void saveComplianceProfileDerivesAccountFromSessionAndForwardsTheFields() {
        UUID accountId = UUID.randomUUID();
        SessionService sessions = mock(SessionService.class);
        KycService kyc = mock(KycService.class);
        when(sessions.authenticate("cookie")).thenReturn(new AuthenticatedSession("cookie",
                new SessionView(accountId, InitialMode.SELLER, Instant.now(), Instant.now().plusSeconds(60))));
        when(kyc.saveComplianceProfile(accountId, "01310100", "1990-05-20", 150_000L))
                .thenReturn(new KycView(accountId, KycStatus.PENDING, null, List.of()));

        var response = new KycController(kyc, sessions).saveComplianceProfile("cookie",
                new KycController.ComplianceProfileRequest("01310100", "1990-05-20", 150_000L));

        assertThat(response.accountId()).isEqualTo(accountId);
        verify(kyc).saveComplianceProfile(accountId, "01310100", "1990-05-20", 150_000L);
    }
}
