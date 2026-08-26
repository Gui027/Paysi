package com.paysi.identity.session.port;

import com.paysi.identity.session.domain.UserSession;

import java.util.Optional;
import java.util.UUID;

public interface SessionStore {
    void save(String tokenHash, UserSession session);
    Optional<UserSession> find(String tokenHash);
    void delete(String tokenHash);
    void deleteAllForAccount(UUID accountId);
}
