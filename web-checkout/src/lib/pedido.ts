import { apiRequest } from "./api.js";
import { PersonType } from "./checkout.js";

export type EnderecoInput = {
  zipCode: string;
  street: string;
  number: string;
  complement: string;
  district: string;
  city: string;
  state: string;
};

export type CompradorInput = {
  name: string;
  email: string;
  personType: PersonType;
  taxId: string;
  legalName?: string;
  municipalReg?: string;
  address?: EnderecoInput;
};

export type PedidoInput = {
  buyer: CompradorInput;
  method: "CARD" | "PIX" | "BOLETO";
  installments: number;
  coupon: string | null;
  termsHash: string;
};

export type PedidoCriado = {
  orderId: string;
  status: string;
};

/**
 * Depende da criação de pedido do checkout (card BE-05.2), que ainda não existe
 * em nenhum branch do backend — ver decisão registrada no PR do FE-13.1. O
 * payload já segue o contrato-alvo documentado em docs/02-arquitetura-e-dados.md
 * (§4.3), então nenhuma mudança de forma deve ser necessária quando aquele
 * endpoint for implementado; até lá, esta chamada retorna 404/erro de rede.
 */
export function criarPedido(slug: string, input: PedidoInput, idempotencyKey: string) {
  return apiRequest<PedidoCriado>(`/v1/checkout/${encodeURIComponent(slug)}/orders`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify(input),
  });
}
