package com.paysi.identity.session.port;

public interface SessionTokenService {
    String generate();
    String hash(String rawToken);
}
