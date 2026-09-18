package com.paysi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeçalhos de resposta exigidos pelo documento 3 (§2.2, AM-20, RNF-018,
 * RNF-033): TLS/HSTS em toda comunicação e política de segurança de conteúdo
 * restritiva na página de checkout, sem origem externa aceita para imagem de
 * personalização (RNF-034 é imposto do lado do upload — aqui é o navegador do
 * comprador que passa a recusar a carga caso alguém volte a permitir).
 *
 * <p>{@code Strict-Transport-Security} é seguro de emitir sempre: um cliente que
 * já está em HTTP simples ignora o cabeçalho, e em produção a aplicação já fica
 * atrás de TLS terminado no balanceador. Não afeta o ambiente local, que não é
 * servido por HTTPS.
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {
    private final String contentSecurityPolicy;

    public SecurityHeadersFilter(
            @Value("${paysi.security.content-security-policy:"
                    + "default-src 'self'; img-src 'self' data:; script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; "
                    + "connect-src 'self'; base-uri 'self'; object-src 'none'}") String contentSecurityPolicy) {
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        chain.doFilter(request, response);
    }
}
