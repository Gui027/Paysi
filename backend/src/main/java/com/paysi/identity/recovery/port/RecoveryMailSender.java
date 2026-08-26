package com.paysi.identity.recovery.port;

public interface RecoveryMailSender {
    void sendPasswordReset(String destinationEmail, String rawToken);
}
