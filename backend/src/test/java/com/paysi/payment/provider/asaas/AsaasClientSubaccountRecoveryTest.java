package com.paysi.payment.provider.asaas;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AsaasClientSubaccountRecoveryTest {
    @Test
    void locatesTheExactWalletAndCapturesTheOneTimeCredential() {
        RestTemplate http = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        AsaasClient client = new AsaasClient(http, new ObjectMapper());

        server.expect(requestTo("/accounts?walletId=wallet_abc&limit=2"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[{"id":"acc_123","walletId":"wallet_abc"}],"hasMore":false,"totalCount":1}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("/accounts/acc_123/accessTokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"name\":\"Paysi Backend\"}"))
                .andRespond(withSuccess("""
                        {"id":"token-id","apiKey":"new-sub-key"}
                        """, MediaType.APPLICATION_JSON));

        var account = client.findSubaccountByWalletId("wallet_abc");
        var token = client.createSubaccountAccessToken(account.id());

        assertThat(account.id()).isEqualTo("acc_123");
        assertThat(token.apiKey()).isEqualTo("new-sub-key");
        server.verify();
    }
}
