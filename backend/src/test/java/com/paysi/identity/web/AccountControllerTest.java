package com.paysi.identity.web;

import com.paysi.identity.app.AccountCreated;
import com.paysi.identity.app.SignUpService;
import com.paysi.identity.domain.InitialMode;
import com.paysi.identity.domain.KycStatus;
import com.paysi.identity.domain.PersonType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final String TEST_PASSWORD = "integration-test-value";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SignUpService signUpService;

    @Test
    void returns201WithoutPasswordForValidRequest() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(signUpService.signUp(any())).thenReturn(new AccountCreated(accountId, "Pessoa Teste",
                "pessoa@exemplo.com", PersonType.PF, KycStatus.PENDING, InitialMode.SELLER,
                "TRANSACIONAL"));

        mvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Pessoa Teste",
                                  "email": "pessoa@exemplo.com",
                                  "password": "%s",
                                  "personType": "PF",
                                  "taxId": "52998224725",
                                  "initialMode": "SELLER",
                                  "termsHash": "sha256:termos-v1"
                                }
                                """.formatted(TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.activeMode").value("SELLER"))
                .andExpect(jsonPath("$.plan").value("TRANSACIONAL"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void mapsInvalidFieldsToStableValidationEnvelope() throws Exception {
        mvc.perform(post("/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "invalido",
                                  "password": "curta",
                                  "personType": "PF",
                                  "taxId": "",
                                  "initialMode": "SELLER",
                                  "termsHash": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors", hasSize(5)));

        verifyNoInteractions(signUpService);
    }
}
