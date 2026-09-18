package com.paysi.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    @Test
    void generatesAndEchoesACorrelationIdWhenTheClientSendsNone() throws Exception {
        var filter = new CorrelationIdFilter();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String header = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(header).isNotBlank();
        // O MDC precisa ter sido limpo ao final da requisição, senão vaza para a
        // próxima requisição atendida pela mesma thread do pool.
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void echoesTheClientSuppliedCorrelationIdInstead() throws Exception {
        var filter = new CorrelationIdFilter();
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "meu-id-de-rastreio");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("meu-id-de-rastreio");
    }

    @Test
    void putsTheCorrelationIdInMdcWhileTheChainRuns() throws Exception {
        var filter = new CorrelationIdFilter();
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "id-visivel-durante-a-chain");
        var response = new MockHttpServletResponse();
        var seenInChain = new String[1];
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seenInChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(seenInChain[0]).isEqualTo("id-visivel-durante-a-chain");
    }
}
