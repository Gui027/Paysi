import { apiRequest } from "./api.js";
import type { DadosCartao } from "./cartao.js";

export type StatusCobranca = "approved" | "declined" | "pending" | "PENDING" | "PAID" | "FAILED";

export type ThreeDsView = {
  required: boolean;
  status: string;
  challengeUrl: string | null;
};

export type CobrancaIniciada = {
  chargeId: string;
  method: "CARD" | "BOLETO" | "PIX";
  status: string;
  idempotentReplay: boolean;
  threeDs: ThreeDsView | null;
  boletoBarcode: string | null;
  boletoUrl: string | null;
  pixQrCode: string | null;
  expiresAt: string | null;
};

export type IniciarCobrancaInput = {
  cardToken?: string | null;
  deviceKey: string;
  termsHash: string;
  termsAcceptedAt: string;
};

export function iniciarCobranca(orderId: string, input: IniciarCobrancaInput) {
  return apiRequest<CobrancaIniciada>(`/v1/orders/${encodeURIComponent(orderId)}/charge`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export type CartaoTokenizado = { cardToken: string; brand: string | null; last4: string | null };

/** Envia o cartão ao backend, que troca por um token do provedor na hora e não guarda nada. */
export function tokenizarCartao(orderId: string, dados: DadosCartao) {
  return apiRequest<CartaoTokenizado>(`/v1/orders/${encodeURIComponent(orderId)}/card-token`, {
    method: "POST",
    body: JSON.stringify(dados),
  });
}

export type ConfirmarTresDsInput = {
  challengeToken: string;
  deviceKey: string;
  termsHash: string;
  termsAcceptedAt: string;
};

export function confirmarTresDs(chargeId: string, input: ConfirmarTresDsInput) {
  return apiRequest<CobrancaIniciada>(`/v1/charges/${encodeURIComponent(chargeId)}/confirm-3ds`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

const DEVICE_KEY_STORAGE = "paysi.checkout.device-key";

export function obterChaveDeDispositivo(): string {
  const existing = sessionStorage.getItem(DEVICE_KEY_STORAGE);
  if (existing) return existing;
  const created = crypto.randomUUID();
  sessionStorage.setItem(DEVICE_KEY_STORAGE, created);
  return created;
}

export function cobrancaAprovada(cobranca: CobrancaIniciada): boolean {
  return cobranca.status === "approved" || cobranca.status === "PAID";
}

export function cobrancaRecusada(cobranca: CobrancaIniciada): boolean {
  return cobranca.status === "declined" || cobranca.status === "FAILED";
}

export function exigeDesafioTresDs(cobranca: CobrancaIniciada): boolean {
  return Boolean(cobranca.threeDs?.required && cobranca.threeDs.challengeUrl);
}
