package com.paysi.security.mfa.port;

public interface SecretProtector { byte[] encrypt(byte[] clear); byte[] decrypt(byte[] encrypted); }
