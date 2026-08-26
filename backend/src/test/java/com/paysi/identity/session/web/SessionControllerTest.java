package com.paysi.identity.session.web;

import com.paysi.core.error.UnauthorizedException;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.session.app.AuthenticatedSession;
import com.paysi.identity.session.app.SessionService;
import com.paysi.identity.session.app.SessionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SessionService service;

    @Test
    void loginReturnsHttpOnlyCookieAndSessionWithoutTokenInBody() throws Exception {
        when(service.login(eq("user@example.com"), eq("correct"), eq(InitialMode.SELLER)))
                .thenReturn(authenticated());

        mvc.perform(post("/v1/sessions").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"user@example.com","password":"correct","initialMode":"SELLER"}
                        """))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("paysi_session=raw-token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.activeMode").value("SELLER"))
                .andExpect(jsonPath("$.rawToken").doesNotExist());
    }

    @Test
    void missingSessionReturnsStable401() throws Exception {
        when(service.authenticate(any())).thenThrow(new UnauthorizedException("SESSION_INVALID",
                "Sessão ausente ou expirada"));

        mvc.perform(get("/v1/sessions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    private static AuthenticatedSession authenticated() {
        Instant now = Instant.parse("2026-08-26T12:00:00Z");
        return new AuthenticatedSession("raw-token", new SessionView(UUID.randomUUID(), InitialMode.SELLER,
                now, now.plusSeconds(43_200)));
    }
}
