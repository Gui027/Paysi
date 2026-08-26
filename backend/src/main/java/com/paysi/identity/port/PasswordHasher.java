package com.paysi.identity.port;

/** AM-13: toda senha é hasheada com Argon2id antes de tocar o banco. */
public interface PasswordHasher {
    String hash(String rawPassword);
}
