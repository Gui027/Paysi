package com.paysi.payment.provider.asaas.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/** A credencial só aparece nesta resposta; aliases toleram as duas grafias usadas na documentação. */
public record AsaasAccessTokenResponse(String id, @JsonAlias({"access_token", "accessToken"}) String apiKey) {
}
