package com.paysi.admin.web;

import com.paysi.admin.app.AdminAuthService;
import com.paysi.admin.app.AdminService;
import com.paysi.core.error.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
class AdminControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AdminAuthService auth;

    @MockitoBean
    private AdminService admin;

    @Test
    void ordinarySessionCannotCallAdministrativeApi() throws Exception {
        when(auth.authenticate(isNull(), isNull(), anySet()))
                .thenThrow(new UnauthorizedException(
                        "ADMIN_CREDENTIALS_INVALID", "Credenciais administrativas inválidas"));

        mvc.perform(get("/v1/admin/search")
                        .cookie(new jakarta.servlet.http.Cookie("paysi_session", "ordinary-user"))
                        .param("query", "buyer@example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ADMIN_CREDENTIALS_INVALID"));
    }
}
