package com.paysi.security.mfa.adapter;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class TotpCodesTest {
    @Test
    void matchesRfc6238VectorAndRejectsMalformedCode() {
        var codes=new TotpCodes(); byte[] secret="12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        assertThat(codes.code(secret,1)).isEqualTo("287082");
        assertThat(codes.matches(secret,"287082",Instant.ofEpochSecond(59))).isTrue();
        assertThat(codes.matches(secret,"not-a-code",Instant.ofEpochSecond(59))).isFalse();
    }
}
