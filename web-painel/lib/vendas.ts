import { apiRequest, CursorPage } from "./api";

export type OrderStatus =
  | "PENDING"
  | "PAID"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED"
  | "CHARGEBACK"
  | "FAILED";

export type PaymentMethod = "PIX" | "BOLETO" | "CARD";

export type OrderSummary = {
  id: string;
  buyerNameMasked: string;
  productName: string;
  productId: string;
  method: PaymentMethod;
  status: OrderStatus;
  paidCents: number;
  grossCents: number;
  discountCents: number;
  chargesCount: number;
  createdAt: string;
};

export type SplitParticipant = {
  recipient: string;
  role: "SELLER" | "AFFILIATE" | "PLATFORM";
  amountCents: number;
};

export type ReceivableDetail = {
  id: string;
  installmentNumber: number;
  amountCents: number;
  bucket: "GUARANTEE" | "PENDING" | "RESERVE" | "AVAILABLE";
  availableAt: string;
  status: "SCHEDULED" | "AVAILABLE" | "PAID";
};

export type ThreeDsEvidence = {
  version: string;
  eci: string;
  cavvPresent: boolean;
  liabilityShifted: boolean;
};

export type ChargeEvent = {
  id: string;
  type: string;
  description: string;
  occurredAt: string;
};

export type ChargeDetail = {
  id: string;
  sequence: number;
  status: OrderStatus;
  method: PaymentMethod;
  amountCents: number;
  refundedCents: number;
  confirmedAt: string | null;
  split: SplitParticipant[];
  receivables: ReceivableDetail[];
  threeDS: ThreeDsEvidence | null;
  events: ChargeEvent[];
};

export type OrderDetail = {
  id: string;
  buyer: {
    nameMasked: string;
    emailMasked: string;
    documentMasked: string;
  };
  product: {
    id: string;
    name: string;
  };
  offer: {
    id: string;
    title: string;
    slug: string;
  };
  affiliation: {
    id: string;
    affiliateName: string;
    commissionCents: number;
    rateBps: number;
  } | null;
  terms: {
    version: string;
    acceptedAt: string;
  } | null;
  grossCents: number;
  discountCents: number;
  paidCents: number;
  status: OrderStatus;
  createdAt: string;
  charges: ChargeDetail[];
};

export type OrderPeriodPreset = "" | "today" | "7d" | "30d";

export type OrderFilters = {
  query: string;
  status: "" | OrderStatus;
  method: "" | PaymentMethod;
  productId: string;
  period: OrderPeriodPreset;
};

export const orderStatusLabel: Record<OrderStatus, string> = {
  PENDING: "Aguardando pagamento",
  PAID: "Paga",
  REFUNDED: "Reembolsada",
  PARTIALLY_REFUNDED: "Reembolso parcial",
  CHARGEBACK: "Contestada",
  FAILED: "Falhou",
};

export const paymentMethodLabel: Record<PaymentMethod, string> = {
  PIX: "Pix",
  BOLETO: "Boleto",
  CARD: "Cartão de crédito",
};

export const splitRoleLabel: Record<SplitParticipant["role"], string> = {
  SELLER: "Vendedor",
  AFFILIATE: "Afiliado",
  PLATFORM: "Plataforma",
};

export function maskName(name: string): string {
  if (!name) return "";
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    const first = parts[0];
    return first.length > 2 ? `${first.slice(0, 2)}***` : `${first}*`;
  }
  return `${parts[0]} ${parts[parts.length - 1].slice(0, 1)}***`;
}

export function maskEmail(email: string): string {
  if (!email || !email.includes("@")) return "";
  const [local, domain] = email.split("@");
  const visible = local.length > 2 ? local.slice(0, 2) : local.slice(0, 1);
  return `${visible}***@${domain}`;
}

export function maskDocument(doc: string): string {
  const clean = doc.replace(/\D/g, "");
  if (clean.length === 11) {
    return `***.${clean.slice(3, 6)}.${clean.slice(6, 9)}-**`;
  }
  if (clean.length === 14) {
    return `**.${clean.slice(2, 5)}.${clean.slice(5, 8)}/****-**`;
  }
  return "***";
}

export function orderMatchesFilters(
  order: OrderSummary,
  filters: OrderFilters,
): boolean {
  const q = filters.query.trim().toLowerCase();
  const matchesQuery =
    !q ||
    order.id.toLowerCase().includes(q) ||
    order.productName.toLowerCase().includes(q) ||
    order.buyerNameMasked.toLowerCase().includes(q);

  const matchesStatus = !filters.status || order.status === filters.status;
  const matchesMethod = !filters.method || order.method === filters.method;
  const matchesProduct =
    !filters.productId || order.productId === filters.productId;

  return matchesQuery && matchesStatus && matchesMethod && matchesProduct;
}

export function listOrders(
  filters?: Partial<OrderFilters>,
  cursor?: string,
  limit = 20,
) {
  const query = new URLSearchParams({ limit: String(limit) });
  if (cursor) query.set("cursor", cursor);
  if (filters?.status) query.set("status", filters.status);
  if (filters?.method) query.set("method", filters.method);
  if (filters?.productId) query.set("productId", filters.productId);
  if (filters?.period) query.set("period", filters.period);
  if (filters?.query) query.set("query", filters.query);

  return apiRequest<CursorPage<OrderSummary>>(`/v1/orders?${query}`);
}

export function getOrder(orderId: string) {
  return apiRequest<OrderDetail>(`/v1/orders/${encodeURIComponent(orderId)}`);
}

export type RefundKind = "TOTAL" | "PARTIAL";

export type RefundInput = {
  chargeId: string;
  kind: RefundKind;
  amountCents?: number;
  reason: string;
  idempotencyKey: string;
};

export type RefundValidationResult = {
  isValid: boolean;
  error?: string;
};

export type RefundResult = {
  id: string;
  chargeId: string;
  amountCents: number;
  kind: RefundKind;
  status: "PENDING" | "COMPLETED" | "REJECTED";
  refundedAt: string;
};

export type InvoiceStatus = "ISSUED" | "PENDING" | "ERROR";

export type InvoiceDetail = {
  id: string;
  chargeId: string;
  number: string | null;
  status: InvoiceStatus;
  pdfUrl: string | null;
  xmlUrl: string | null;
  errorMessage: string | null;
  retryable: boolean;
  issuedAt: string | null;
};

export const invoiceStatusLabel: Record<InvoiceStatus, string> = {
  ISSUED: "Emitida",
  PENDING: "Processando emissão",
  ERROR: "Falha na emissão fiscal",
};

export function validateRefundInput(
  input: RefundInput,
  maxRefundableCents: number,
): RefundValidationResult {
  if (!input.reason || input.reason.trim().length < 3) {
    return {
      isValid: false,
      error: "Informe o motivo do reembolso (mínimo de 3 caracteres).",
    };
  }

  if (input.kind === "PARTIAL") {
    if (!input.amountCents || input.amountCents <= 0) {
      return {
        isValid: false,
        error: "Informe um valor válido para o reembolso parcial.",
      };
    }
    if (input.amountCents > maxRefundableCents) {
      return {
        isValid: false,
        error:
          "O valor informado excede o saldo disponível para reembolso desta cobrança.",
      };
    }
  }

  return { isValid: true };
}

export function getRefundConfirmationMessage(
  kind: RefundKind,
  amountFormatted: string,
): string {
  if (kind === "TOTAL") {
    return `Você está prestes a realizar o estorno integral no valor de ${amountFormatted}. O acesso do comprador será revogado e os lançamentos no razão serão estornados proporcionalmente.`;
  }
  return `Você está prestes a realizar um estorno parcial de ${amountFormatted}. A memória financeira da cobrança será recalculada mantendo a assinatura/compra ativa.`;
}

export function createRefund(chargeId: string, input: RefundInput) {
  return apiRequest<RefundResult>(
    `/v1/charges/${encodeURIComponent(chargeId)}/refunds`,
    {
      method: "POST",
      headers: { "Idempotency-Key": input.idempotencyKey },
      body: JSON.stringify({
        amountCents: input.kind === "PARTIAL" ? input.amountCents : undefined,
        reason: input.reason.trim(),
        kind: input.kind,
      }),
    },
  );
}

export function listInvoices(orderId: string) {
  return apiRequest<InvoiceDetail[]>(
    `/v1/orders/${encodeURIComponent(orderId)}/invoices`,
  );
}

export function retryInvoice(invoiceId: string) {
  return apiRequest<InvoiceDetail>(
    `/v1/invoices/${encodeURIComponent(invoiceId)}/retry`,
    {
      method: "POST",
    },
  );
}
