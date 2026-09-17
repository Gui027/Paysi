package com.paysi.fiscal.app;

public record FiscalProfileCommand(String municipalityCode, String municipalRegistration, String serviceItem,
                                    Integer taxBps, String taxRegime, String credentialRef) {
}
