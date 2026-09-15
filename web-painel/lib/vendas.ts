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

export function orderMatchesFilters(order: OrderSummary, filters: OrderFilters): boolean {
  const q = filters.query.trim().toLowerCase();
  const matchesQuery =
    !q ||
    order.id.toLowerCase().includes(q) ||
    order.productName.toLowerCase().includes(q) ||
    order.buyerNameMasked.toLowerCase().includes(q);

  const matchesStatus = !filters.status || order.status === filters.status;
  const matchesMethod = !filters.method || order.method === filters.method;
  const matchesProduct = !filters.productId || order.productId === filters.productId;

  return matchesQuery && matchesStatus && matchesMethod && matchesProduct;
}

export function listOrders(filters?: Partial<OrderFilters>, cursor?: string, limit = 20) {
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

