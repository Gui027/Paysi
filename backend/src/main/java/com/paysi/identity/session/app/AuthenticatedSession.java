package com.paysi.identity.session.app;

public record AuthenticatedSession(String rawToken, SessionView session) {
}
