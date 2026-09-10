import { apiRequest } from "./api.js";
import { PaymentMethod } from "./checkout.js";

export type SimulacaoInput = {
  method: PaymentMethod;
  installments: number;
  couponCode: string;
};

export type SimulacaoCheckout = {
  grossCents: number;
  discountCents: number;
  paidCents: number;
  providerFeeCents: number;
  sellerFeeCents: number;
  affiliateFeeCents: number;
  sellerAmountCents: number;
  availableAt: string;
};

/** Os valores exibidos vêm integralmente da API; o cliente não calcula descontos ou taxas. */
export function simularCheckout(slug: string, input: SimulacaoInput) {
  return apiRequest<SimulacaoCheckout>(`/v1/checkout/${encodeURIComponent(slug)}/simulation`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}
