package com.paysi.identity.recovery.port;

public interface RecoveryTokenService {
    String generate();
    String hash(String rawToken);
}
