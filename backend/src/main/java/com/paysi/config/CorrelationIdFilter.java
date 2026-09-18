package com.paysi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Identificador de correlação propagado do checkout até a chamada ao provedor
 * (RNF-028). Aceita o identificador do cliente quando presente em {@code
 * X-Correlation-Id} — o checkout ou um proxy upstream pode já ter gerado um —
 * senão gera um novo. Coloca no MDC para todo log da requisição, devolve no
 * cabeçalho de resposta e limpa ao final para não vazar para a próxima
 * requisição atendida pela mesma thread do pool.
 *
 * <p>Roda antes de qualquer outro filtro da aplicação ({@link Ordered#HIGHEST_PRECEDENCE})
 * para que o identificador já exista quando {@link ApiError} for construído em
 * qualquer ponto do processamento, inclusive erro lançado por outro filtro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming == null || incoming.isBlank()) ? newId() : incoming.trim();
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
