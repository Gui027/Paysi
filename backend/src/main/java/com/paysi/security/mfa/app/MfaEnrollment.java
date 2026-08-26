package com.paysi.security.mfa.app;
import java.util.List;
public record MfaEnrollment(String secret, String otpauthUri, List<String> recoveryCodes) { }
