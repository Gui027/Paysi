package com.paysi.identity.domain;

/**
 * RF-001: modo escolhido no cadastro. Não é persistido em {@code accounts} — o modo
 * ativo é dado de sessão (RF-004, alternância sem novo login), introduzido em BE-01.2.
 */
public enum InitialMode {
    SELLER,
    AFFILIATE
}
