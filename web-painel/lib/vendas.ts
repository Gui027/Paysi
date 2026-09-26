import { apiRequest, ApiRequestError } from "./api";

export type SaleStatus = "PENDING" | "PAID" | "FAILED" | "EXPIRED" | "PARTIALLY_REFUNDED" | "REFUNDED" | "CHARGEBACK";
export type SaleMethod = "PIX" | "CARD" | "BOLETO";
export type SalesTab = "approved" | "all";

export type SaleRow = {
  id: string;
  code: string;
  createdAt: string;
  approvedAt: string | null;
  status: SaleStatus;
  productName: string;
  productId: string;
  offerName: string | null;
  method: SaleMethod;
  installments: number;
  buyerName: string;
  buyerEmail: string;
  netCents: number;
};

export type SalesPage = {
  items: SaleRow[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  summary: { count: number; netCents: number };
};

export type PayoutState = "WAITING_PAYMENT" | "TO_RELEASE" | "RELEASED" | "REFUNDED" | "NOT_APPLICABLE";

export type RefundRow = {
  id: string;
  chargeId: string;
  saleCode: string;
  productName: string;
  buyerName: string;
  buyerEmail: string;
  amountCents: number;
  reason: string | null;
  status: "PENDING" | "SUCCEEDED" | "FAILED";
  requestedBy: "BUYER" | "SELLER" | "ADMIN" | "SYSTEM";
  createdAt: string;
  settledAt: string | null;
};

export type RefundsPage = { items: RefundRow[]; page: number; size: number; total: number; totalPages: number };

export type SaleDetail = {
  id: string;
  code: string;
  status: SaleStatus;
  type: "PRODUCER";
  productName: string;
  productId: string;
  offerName: string | null;
  method: SaleMethod;
  installments: number;
  createdAt: string;
  approvedAt: string | null;
  availableAt: string | null;
  reference: string | null;
  cycleNumber: number | null;
  subscriptionId: string | null;
  couponCode: string | null;
  buyer: { name: string; email: string; phone: string | null; taxId: string; personType: "PF" | "PJ"; ip: string | null };
  amounts: {
    basePriceCents: number;
    discountCents: number;
    paidCents: number;
    feesCents: number;
    affiliateCents: number;
    sellerCents: number;
    refundedCents: number;
    netCents: number;
  };
  split: { name: string; role: "SELLER" | "AFFILIATE"; amountCents: number }[];
  payoutState: PayoutState;
  canRefund: boolean;
  refunds: RefundRow[];
};

export type SalesQuery = {
  tab: SalesTab;
  q: string;
  statuses: SaleStatus[];
  method: "" | SaleMethod;
  productId: string;
  from: string;
  to: string;
  page: number;
};

export const emptySalesQuery: SalesQuery = { tab: "approved", q: "", statuses: [], method: "", productId: "", from: "", to: "", page: 1 };

export const saleStatusLabel: Record<SaleStatus, string> = {
  PAID: "Pago",
  PENDING: "Aguardando pagamento",
  FAILED: "Recusado",
  EXPIRED: "Expirado",
  PARTIALLY_REFUNDED: "Reembolso parcial",
  REFUNDED: "Reembolsado",
  CHARGEBACK: "Chargeback",
};

export const saleStatusTone: Record<SaleStatus, "success" | "warning" | "danger" | "neutral"> = {
  PAID: "success",
  PENDING: "warning",
  FAILED: "danger",
  EXPIRED: "neutral",
  PARTIALLY_REFUNDED: "warning",
  REFUNDED: "neutral",
  CHARGEBACK: "danger",
};

export const saleMethodLabel: Record<SaleMethod, string> = { PIX: "Pix", CARD: "Cartão de crédito", BOLETO: "Boleto" };

export const refundStatusLabel: Record<RefundRow["status"], string> = { SUCCEEDED: "Concluído", PENDING: "Em processamento", FAILED: "Falhou" };
export const refundOriginLabel: Record<RefundRow["requestedBy"], string> = { SELLER: "Você", BUYER: "Comprador", ADMIN: "Paysi", SYSTEM: "Sistema" };

export const payoutLabel: Record<PayoutState, string> = {
  WAITING_PAYMENT: "Aguardando pagamento",
  TO_RELEASE: "A liberar",
  RELEASED: "Liberado",
  REFUNDED: "Devolvido ao comprador",
  NOT_APPLICABLE: "Sem repasse",
};

/** Só monta a query string; toda conta com dinheiro (líquido, total, taxas) vem pronta do backend. */
export function salesParams(query: SalesQuery, paged = true): URLSearchParams {
  const params = new URLSearchParams({ tab: query.tab });
  if (query.q.trim()) params.set("q", query.q.trim());
  query.statuses.forEach(status => params.append("status", status));
  if (query.method) params.set("method", query.method);
  if (query.productId) params.set("productId", query.productId);
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (paged) params.set("page", String(query.page));
  return params;
}

export function listSales(query: SalesQuery) {
  return apiRequest<SalesPage>(`/v1/sales?${salesParams(query)}`);
}

export function getSale(chargeId: string) {
  return apiRequest<SaleDetail>(`/v1/sales/${encodeURIComponent(chargeId)}`);
}

export function listRefunds(q: string, statuses: RefundRow["status"][], page: number) {
  const params = new URLSearchParams({ page: String(page) });
  if (q.trim()) params.set("q", q.trim());
  statuses.forEach(status => params.append("status", status));
  return apiRequest<RefundsPage>(`/v1/refunds?${params}`);
}

export type RefundResult = { refundId: string; status: string; chargeStatus: string; chargeRefundedCents: number };

/** amountCents nulo reembolsa o valor total que ainda pode ser devolvido. */
export function refundSale(chargeId: string, amountCents: number | null, reason: string, idempotencyKey: string) {
  return apiRequest<RefundResult>(`/v1/charges/${encodeURIComponent(chargeId)}/refunds`, {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ amountCents, reason: reason.trim() || null }),
  });
}

/** Baixa o CSV do filtro atual (o servidor limita a 10.000 linhas). */
export async function downloadSalesCsv(query: SalesQuery): Promise<Blob> {
  const response = await fetch(`/api/v1/sales/export?${salesParams(query, false)}`, {
    credentials: "include",
    headers: { Accept: "text/csv" },
  });
  if (!response.ok) {
    const body = response.headers.get("content-type")?.includes("json") ? await response.json() : {};
    throw new ApiRequestError(response.status, body);
  }
  return response.blob();
}

// ---------- formatação (só texto) ----------

export function formatDocumento(value: string | null | undefined): string {
  const digits = (value ?? "").replace(/\D/g, "");
  if (digits.length === 11) return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9)}`;
  if (digits.length === 14) return `${digits.slice(0, 2)}.${digits.slice(2, 5)}.${digits.slice(5, 8)}/${digits.slice(8, 12)}-${digits.slice(12)}`;
  return value ?? "";
}

/** Celular guardado só com dígitos (com ou sem 55) -> "+55 27 99951-3505". */
export function formatTelefone(value: string | null | undefined): string {
  let digits = (value ?? "").replace(/\D/g, "");
  if (digits.length >= 12 && digits.startsWith("55")) digits = digits.slice(2);
  if (digits.length === 11) return `+55 ${digits.slice(0, 2)} ${digits.slice(2, 7)}-${digits.slice(7)}`;
  if (digits.length === 10) return `+55 ${digits.slice(0, 2)} ${digits.slice(2, 6)}-${digits.slice(6)}`;
  return value ?? "";
}

export function whatsappUrl(value: string | null | undefined): string | null {
  let digits = (value ?? "").replace(/\D/g, "");
  if (digits.length < 10) return null;
  if (digits.length <= 11) digits = `55${digits}`;
  return `https://wa.me/${digits}`;
}

/** Números de página a exibir: 1 2 3 4 5 … 33, sempre com a atual visível. */
export function paginasVisiveis(atual: number, total: number): (number | "…")[] {
  if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1);
  const pages = new Set<number>([1, total, atual - 1, atual, atual + 1]);
  if (atual <= 3) [2, 3, 4, 5].forEach(page => pages.add(page));
  if (atual >= total - 2) [total - 1, total - 2, total - 3, total - 4].forEach(page => pages.add(page));
  const sorted = [...pages].filter(page => page >= 1 && page <= total).sort((a, b) => a - b);
  const result: (number | "…")[] = [];
  sorted.forEach((page, index) => {
    if (index > 0 && page - sorted[index - 1]! > 1) result.push("…");
    result.push(page);
  });
  return result;
}
