package com.paysi.fiscal.issuer;

public record IssuerCredentials(String municipalityCode, String municipalRegistration, String serviceItem,
                                 int taxBps, String taxRegime, String credentialRef) {
}
