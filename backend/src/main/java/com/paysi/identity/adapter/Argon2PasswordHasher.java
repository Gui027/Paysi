package com.paysi.identity.adapter;

import com.paysi.identity.port.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** AM-13: Argon2id com os parâmetros recomendados pelo Spring Security (OWASP). */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return encoder.matches(rawPassword, passwordHash);
    }
}
