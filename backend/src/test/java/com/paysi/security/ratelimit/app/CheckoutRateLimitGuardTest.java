package com.paysi.security.ratelimit.app;

import com.paysi.core.error.TooManyRequestsException;
import com.paysi.security.ratelimit.port.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

class CheckoutRateLimitGuardTest {

    @Test
    void allowsWhenEveryKeyIsUnderLimit() {
        var limiter = mock(RateLimiter.class);
        when(limiter.allow(any(), anyInt(), any())).thenReturn(true);
        var guard = new CheckoutRateLimitGuard(limiter, 30, 10, 15, 60);

        guard.checkOrderAttempt("1.2.3.4", "12345678900", "device-1");
        // não lança: nenhuma das três chaves estourou
    }

    @Test
    void blocksWhenIpKeyIsExhausted() {
        var limiter = mock(RateLimiter.class);
        when(limiter.allow(eq("checkout:order:ip:1.2.3.4"), anyInt(), any())).thenReturn(false);
        when(limiter.allow(eq("checkout:order:taxid:12345678900"), anyInt(), any())).thenReturn(true);
        var guard = new CheckoutRateLimitGuard(limiter, 30, 10, 15, 60);

        assertThatThrownBy(() -> guard.checkOrderAttempt("1.2.3.4", "12345678900", "device-1"))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting(error -> ((TooManyRequestsException) error).code())
                .isEqualTo("RATE_LIMITED");
    }

    @Test
    void blocksWhenTaxIdKeyIsExhausted() {
        var limiter = mock(RateLimiter.class);
        when(limiter.allow(eq("checkout:order:ip:1.2.3.4"), anyInt(), any())).thenReturn(true);
        when(limiter.allow(eq("checkout:order:taxid:12345678900"), anyInt(), any())).thenReturn(false);
        var guard = new CheckoutRateLimitGuard(limiter, 30, 10, 15, 60);

        assertThatThrownBy(() -> guard.checkOrderAttempt("1.2.3.4", "12345678900", "device-1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void toleratesMissingDeviceId() {
        var limiter = mock(RateLimiter.class);
        when(limiter.allow(any(), anyInt(), any())).thenReturn(true);
        var guard = new CheckoutRateLimitGuard(limiter, 30, 10, 15, 60);

        guard.checkOrderAttempt("1.2.3.4", "12345678900", null);
    }
}
