package com.paysi.identity.recovery.web;

import com.paysi.identity.recovery.app.PasswordRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordRecoveryController.class)
class PasswordRecoveryControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PasswordRecoveryService service;

    @Test
    void requestAlwaysReturnsNoContentAfterAcceptedInput() throws Exception {
        mvc.perform(post("/v1/password-recovery").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isNoContent());
        verify(service).request("user@example.com");
    }

    @Test
    void resetDoesNotReturnTokenOrPassword() throws Exception {
        mvc.perform(post("/v1/password-recovery/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"secret-token","newPassword":"new-password","confirmPassword":"new-password"}
                                """))
                .andExpect(status().isNoContent());
        verify(service).reset("secret-token", "new-password", "new-password");
    }
}
