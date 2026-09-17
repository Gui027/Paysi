import { apiRequest } from "./api.js";

export type ChargeStatusView = {
  chargeId: string;
  method: "CARD" | "BOLETO" | "PIX";
  status: string;
  boletoBarcode: string | null;
  boletoUrl: string | null;
  pixQrCode: string | null;
  expiresAt: string | null;
};

export function consultarStatusCobranca(chargeId: string) {
  return apiRequest<ChargeStatusView>(`/v1/charges/${encodeURIComponent(chargeId)}`);
}

export function statusFinalPago(status: string): boolean {
  return status === "PAID" || status === "approved";
}

export function statusFinalFalhou(status: string): boolean {
  return status === "FAILED" || status === "EXPIRED" || status === "declined";
}
