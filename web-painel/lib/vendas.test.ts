import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { ApiRequestError } from "./api";
import {
  createRefund,
  getOrder,
  getRefundConfirmationMessage,
  listOrders,
  maskDocument,
  maskEmail,
  maskName,
  OrderDetail,
  orderMatchesFilters,
  OrderSummary,
  retryInvoice,
  validateRefundInput,
} from "./vendas";

const sampleOrder: OrderSummary = {
  id: "ord-12345-abcde",
  buyerNameMasked: "Carlos S***",
  productName: "Assinatura Pro",
  productId: "prod-999",
  method: "CARD",
  status: "PAID",
  paidCents: 9900,
  grossCents: 9900,
  discountCents: 0,
  chargesCount: 3,
  createdAt: "2026-09-10T14:30:00Z",
};

const sampleDetail: OrderDetail = {
  id: "ord-12345-abcde",
  buyer: {
    nameMasked: "Carlos S***",
    emailMasked: "ca***@gmail.com",
    documentMasked: "***.456.789-**",
  },
  product: {
    id: "prod-999",
    name: "Assinatura Pro",
  },
  offer: {
    id: "off-888",
    title: "Plano Mensal",
    slug: "plano-mensal",
  },
  affiliation: {
    id: "aff-777",
    affiliateName: "Maria Afiliada",
    commissionCents: 990,
    rateBps: 1000,
  },
  terms: {
    version: "v1.2",
    acceptedAt: "2026-09-10T14:29:55Z",
  },
  grossCents: 9900,
  discountCents: 0,
  paidCents: 9900,
  status: "PAID",
  createdAt: "2026-09-10T14:30:00Z",
  charges: [
    {
      id: "chg-1",
      sequence: 1,
      status: "PAID",
      method: "CARD",
      amountCents: 3300,
      refundedCents: 0,
      confirmedAt: "2026-09-10T14:30:05Z",
      split: [
        { recipient: "Vendedor", role: "SELLER", amountCents: 2640 },
        { recipient: "Maria Afiliada", role: "AFFILIATE", amountCents: 330 },
        { recipient: "Paysi", role: "PLATFORM", amountCents: 330 },
      ],
      receivables: [
        {
          id: "rec-1",
          installmentNumber: 1,
          amountCents: 2640,
          bucket: "AVAILABLE",
          availableAt: "2026-09-12T14:30:00Z",
          status: "AVAILABLE",
        },
      ],
      threeDS: {
        version: "2.2.0",
        eci: "05",
        cavvPresent: true,
        liabilityShifted: true,
      },
      events: [
        {
          id: "evt-1",
          type: "PAYMENT_CONFIRMED",
          description: "Cobrança autorizada e confirmada pelo adquirente",
          occurredAt: "2026-09-10T14:30:05Z",
        },
      ],
    },
  ],
};

const originalFetch = globalThis.fetch;
afterEach(() => {
  globalThis.fetch = originalFetch;
});

test("mascara dados sensíveis de comprador (PII) corretamente", () => {
  assert.equal(maskName("Carlos Eduardo Silva"), "Carlos S***");
  assert.equal(maskName("Ana"), "An***");
  assert.equal(
    maskEmail("carlos.silva@empresa.com.br"),
    "ca***@empresa.com.br",
  );
  assert.equal(maskDocument("12345678901"), "***.456.789-**");
  assert.equal(maskDocument("12345678000199"), "**.345.678/****-**");
});

test("filtra vendas por termo, status, método e produto sem duplicidade", () => {
  assert.equal(
    orderMatchesFilters(sampleOrder, {
      query: "assinatura",
      status: "PAID",
      method: "CARD",
      productId: "prod-999",
      period: "",
    }),
    true,
  );

  assert.equal(
    orderMatchesFilters(sampleOrder, {
      query: "curso",
      status: "PAID",
      method: "CARD",
      productId: "",
      period: "",
    }),
    false,
  );

  assert.equal(
    orderMatchesFilters(sampleOrder, {
      query: "",
      status: "REFUNDED",
      method: "CARD",
      productId: "",
      period: "",
    }),
    false,
  );
});

test("lista vendas com paginação por cursor", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(
      JSON.stringify({ items: [sampleOrder], nextCursor: "page-2" }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  }) as typeof fetch;

  const page = await listOrders({ status: "PAID", method: "CARD" }, "cursor-1");
  assert.equal(page.items.length, 1);
  assert.equal(page.items[0].id, "ord-12345-abcde");
  assert.equal(page.nextCursor, "page-2");
  assert.match(requestedUrl, /cursor=cursor-1/);
  assert.match(requestedUrl, /status=PAID/);
  assert.match(requestedUrl, /method=CARD/);
});

test("busca detalhe da venda com cobranças e memória financeira", async () => {
  globalThis.fetch = (async () =>
    new Response(JSON.stringify(sampleDetail), {
      status: 200,
      headers: { "content-type": "application/json" },
    })) as typeof fetch;

  const detail = await getOrder("ord-12345-abcde");
  assert.equal(detail.id, "ord-12345-abcde");
  assert.equal(detail.charges.length, 1);
  assert.equal(detail.charges[0].split.length, 3);
  assert.equal(detail.charges[0].threeDS?.liabilityShifted, true);
  assert.equal(detail.buyer.documentMasked, "***.456.789-**");
});

test("propaga 404 quando pedido não é encontrado ou pertence a outro vendedor", async () => {
  globalThis.fetch = (async () =>
    new Response(
      JSON.stringify({
        code: "ORDER_NOT_FOUND",
        message: "Pedido não encontrado",
      }),
      { status: 404, headers: { "content-type": "application/json" } },
    )) as typeof fetch;

  await assert.rejects(
    () => getOrder("ord-desconhecida"),
    (err: unknown) => err instanceof ApiRequestError && err.status === 404,
  );
});

test("valida campos de reembolso exigindo motivo e valor válido", () => {
  const emptyReason = validateRefundInput(
    { chargeId: "chg-1", kind: "TOTAL", reason: " ", idempotencyKey: "idem-1" },
    3300,
  );
  assert.equal(emptyReason.isValid, false);
  assert.match(emptyReason.error ?? "", /motivo/i);

  const exceedingPartial = validateRefundInput(
    {
      chargeId: "chg-1",
      kind: "PARTIAL",
      amountCents: 5000,
      reason: "Cancelamento parcial",
      idempotencyKey: "idem-2",
    },
    3300,
  );
  assert.equal(exceedingPartial.isValid, false);
  assert.match(exceedingPartial.error ?? "", /excede/i);

  const validPartial = validateRefundInput(
    {
      chargeId: "chg-1",
      kind: "PARTIAL",
      amountCents: 1500,
      reason: "Acordo comercial",
      idempotencyKey: "idem-3",
    },
    3300,
  );
  assert.equal(validPartial.isValid, true);
});

test("diferencia mensagens de confirmação de estorno total e parcial", () => {
  const totalMsg = getRefundConfirmationMessage("TOTAL", "R$ 100,00");
  const partialMsg = getRefundConfirmationMessage("PARTIAL", "R$ 30,00");
  assert.match(totalMsg, /estorno integral/i);
  assert.match(totalMsg, /revogado/i);
  assert.match(partialMsg, /estorno parcial/i);
  assert.match(partialMsg, /mantendo a assinatura/i);
  assert.notEqual(totalMsg, partialMsg);
});

test("envia reembolso preservando chave de idempotencia contra duplo clique", async () => {
  let capturedHeaders: Record<string, string> = {};
  let capturedBody = "";
  globalThis.fetch = (async (_url, init) => {
    capturedHeaders = (init?.headers ?? {}) as Record<string, string>;
    capturedBody = String(init?.body ?? "");
    return new Response(
      JSON.stringify({
        id: "ref-1",
        chargeId: "chg-1",
        amountCents: 3300,
        kind: "TOTAL",
        status: "COMPLETED",
        refundedAt: "2026-09-15T12:00:00Z",
      }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  }) as typeof fetch;

  const result = await createRefund("chg-1", {
    chargeId: "chg-1",
    kind: "TOTAL",
    reason: "Desistência no prazo",
    idempotencyKey: "idem-uuid-12345",
  });

  assert.equal(result.status, "COMPLETED");
  assert.equal(capturedHeaders["Idempotency-Key"], "idem-uuid-12345");
  assert.match(capturedBody, /Desistência no prazo/);
});

test("reemite nota fiscal sem tratar erro fiscal como erro de pagamento", async () => {
  let requestedUrl = "";
  globalThis.fetch = (async (input) => {
    requestedUrl = String(input);
    return new Response(
      JSON.stringify({
        id: "inv-1",
        chargeId: "chg-1",
        number: "NF-9876",
        status: "ISSUED",
        pdfUrl: "https://notas.paysi.com/9876.pdf",
        xmlUrl: "https://notas.paysi.com/9876.xml",
        errorMessage: null,
        retryable: false,
        issuedAt: "2026-09-15T12:05:00Z",
      }),
      { status: 200, headers: { "content-type": "application/json" } },
    );
  }) as typeof fetch;

  const invoice = await retryInvoice("inv-1");
  assert.equal(invoice.status, "ISSUED");
  assert.equal(invoice.number, "NF-9876");
  assert.match(requestedUrl, /\/v1\/invoices\/inv-1\/retry/);
});
