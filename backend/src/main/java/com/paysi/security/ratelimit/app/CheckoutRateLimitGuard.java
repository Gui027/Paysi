package com.paysi.security.ratelimit.app;

import com.paysi.core.error.TooManyRequestsException;
import com.paysi.security.ratelimit.port.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Guarda de AM-05 (teste de cartão roubado usando o checkout como validador) e
 * AM-22 (criação em massa de contas para abusar de teste gratuito): limite de
 * tentativas de criação de pedido por IP, por CPF/CNPJ e por impressão de
 * dispositivo (documento 3, §1.2 e §1.3).
 *
 * <p>As três chaves são independentes de propósito: IP contém varredura em
 * massa vinda de um único ponto; CPF/CNPJ contém repetição sobre o mesmo
 * documento mesmo com IP rotativo; dispositivo contém as duas coisas por trás
 * de rede compartilhada ou de CPF trocado a cada tentativa.
 */
@Service
public class CheckoutRateLimitGuard {
    private final RateLimiter limiter;
    private final int maxByIp;
    private final int maxByTaxId;
    private final int maxByDevice;
    private final Duration window;

    public CheckoutRateLimitGuard(RateLimiter limiter,
            @Value("${paysi.rate-limit.checkout.max-by-ip:30}") int maxByIp,
            @Value("${paysi.rate-limit.checkout.max-by-tax-id:10}") int maxByTaxId,
            @Value("${paysi.rate-limit.checkout.max-by-device:15}") int maxByDevice,
            @Value("${paysi.rate-limit.checkout.window-seconds:60}") long windowSeconds) {
        this.limiter = limiter;
        this.maxByIp = maxByIp;
        this.maxByTaxId = maxByTaxId;
        this.maxByDevice = maxByDevice;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * @param ip       IP do requisitante, já resolvido pelo controller
     * @param taxId    CPF ou CNPJ do comprador informado no corpo, pode ser nulo
     * @param deviceId impressão de dispositivo (visitorKey), pode ser nula
     */
    public void checkOrderAttempt(String ip, String taxId, String deviceId) {
        if (ip != null && !ip.isBlank() && !limiter.allow("checkout:order:ip:" + ip, maxByIp, window)) {
            throw tooMany("por este endereço IP");
        }
        if (taxId != null && !taxId.isBlank()
                && !limiter.allow("checkout:order:taxid:" + taxId, maxByTaxId, window)) {
            throw tooMany("para este CPF/CNPJ");
        }
        if (deviceId != null && !deviceId.isBlank()
                && !limiter.allow("checkout:order:device:" + deviceId, maxByDevice, window)) {
            throw tooMany("para este dispositivo");
        }
    }

    /**
     * Tokenização de cartão: poucas tentativas por pedido (AM-05 — o checkout não pode virar
     * validador de cartão) e por IP. O limite por pedido é fixo e baixo de propósito.
     */
    public void checkCardTokenAttempt(String ip, java.util.UUID orderId) {
        if (!limiter.allow("checkout:cardtoken:order:" + orderId, 5, window)) {
            throw new TooManyRequestsException("RATE_LIMITED", "Muitas tentativas com o cartão neste pedido", null);
        }
        if (ip != null && !ip.isBlank() && !limiter.allow("checkout:cardtoken:ip:" + ip, maxByIp, window)) {
            throw tooMany("por este endereço IP");
        }
    }

    private static TooManyRequestsException tooMany(String reason) {
        return new TooManyRequestsException("RATE_LIMITED",
                "Muitas tentativas de pedido em pouco tempo " + reason, null);
    }
}
