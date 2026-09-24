package com.paysi.payment.provider;

/** Token opaco do provedor mais o que é seguro exibir (bandeira e últimos 4 dígitos). */
public record CardTokenResult(String token, String brand, String last4) {
}
