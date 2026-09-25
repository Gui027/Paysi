import type { Page } from "@playwright/test";

/**
 * Fixtures/mocks da API do checkout. Interceptamos no limite de
 * src/lib/api.ts (fetch) — o front-end sob teste é o real, só a rede é
 * dublada, já que este repo não tem um backend+banco disponível para subir
 * em CI para um E2E ponta a ponta completo.
 */
export const OFERTA_SLUG = "curso-avancado";

export function contractFixture(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    product: "Curso Avançado de Vendas",
    segment: "DIGITAL",
    chargeType: "ONE_TIME",
    priceCents: 19900,
    cycle: null,
    today: "2026-09-17",
    nextChargeAt: null,
    methods: ["PIX", "CARD", "BOLETO"],
    installments: 3,
    requiredBuyerFields: {
      PF: ["name", "email", "taxId"],
      PJ: ["name", "email", "taxId", "legalName"],
    },
    appearance: {
      logoUrl: null,
      bannerUrl: null,
      sideImageUrl: null,
      primaryColor: "#1c64f2",
      buttonText: "Pagar agora",
    },
    legalTexts: {
      termsUrl: "https://paysi.com.br/termos",
      privacyUrl: "https://paysi.com.br/privacidade",
    },
    ...overrides,
  };
}

export async function mockOferta(page: Page, overrides: Partial<Record<string, unknown>> = {}) {
  await page.route(`**/v1/offers/${OFERTA_SLUG}/checkout`, async (route) => {
    await route.fulfill({ json: contractFixture(overrides) });
  });
}

export async function mockOfertaNotFound(page: Page) {
  await page.route(`**/v1/offers/${OFERTA_SLUG}/checkout`, async (route) => {
    await route.fulfill({ status: 404, json: { code: "OFFER_NOT_FOUND", message: "Oferta não encontrada." } });
  });
}

export async function mockOfertaErro(page: Page) {
  await page.route(`**/v1/offers/${OFERTA_SLUG}/checkout`, async (route) => {
    await route.fulfill({ status: 500, json: { code: "INTERNAL", message: "Erro interno." } });
  });
}

export async function mockPedido(page: Page, orderId = "order_e2e_1") {
  await page.route(`**/v1/checkout/${OFERTA_SLUG}/orders`, async (route) => {
    await route.fulfill({ json: { orderId, status: "CREATED" } });
  });
}

/** Tokenização do cartão (POST /v1/orders/{id}/card-token): devolve só o token, como o backend. */
export async function mockTokenizacaoCartao(page: Page, capturas: { tokenizacao?: string; cobranca?: string } = {}) {
  await page.route(/\/v1\/orders\/.+\/card-token$/, async (route) => {
    capturas.tokenizacao = route.request().postData() ?? "";
    await route.fulfill({ json: { cardToken: "tok_e2e_1", brand: "VISA", last4: "1111" } });
  });
  // Observa (não intercepta): o mock da cobrança é registrado depois e atende antes.
  page.on("request", (request) => {
    if (/\/v1\/orders\/.+\/charge$/.test(request.url())) capturas.cobranca = request.postData() ?? "";
  });
}

export async function mockCobranca(page: Page, resposta: Record<string, unknown>) {
  await page.route(/\/v1\/orders\/.+\/charge$/, async (route) => {
    await route.fulfill({ json: resposta });
  });
  // PixAguardando/BoletoEmitido fazem polling de status com backoff (5s+);
  // mockamos para o caso do teste demorar o suficiente para disparar uma consulta.
  await page.route(/\/v1\/charges\/.+$/, async (route) => {
    await route.fulfill({
      json: {
        chargeId: (resposta as { chargeId?: string }).chargeId ?? "charge_x",
        method: (resposta as { method?: string }).method ?? "PIX",
        status: (resposta as { status?: string }).status ?? "pending",
        boletoBarcode: (resposta as { boletoBarcode?: string | null }).boletoBarcode ?? null,
        boletoUrl: (resposta as { boletoUrl?: string | null }).boletoUrl ?? null,
        pixQrCode: (resposta as { pixQrCode?: string | null }).pixQrCode ?? null,
        expiresAt: (resposta as { expiresAt?: string | null }).expiresAt ?? null,
      },
    });
  });
}

export const COBRANCA_CARTAO_APROVADA = {
  chargeId: "charge_1",
  method: "CARD",
  status: "approved",
  idempotentReplay: false,
  threeDs: { required: false, status: "NOT_REQUIRED", challengeUrl: null },
  boletoBarcode: null,
  boletoUrl: null,
  pixQrCode: null,
  expiresAt: null,
};

export const COBRANCA_CARTAO_RECUSADA = {
  chargeId: "charge_2",
  method: "CARD",
  status: "declined",
  idempotentReplay: false,
  threeDs: { required: false, status: "NOT_REQUIRED", challengeUrl: null },
  boletoBarcode: null,
  boletoUrl: null,
  pixQrCode: null,
  expiresAt: null,
};

export const COBRANCA_PIX = {
  chargeId: "charge_3",
  method: "PIX",
  status: "pending",
  idempotentReplay: false,
  threeDs: null,
  boletoBarcode: null,
  boletoUrl: null,
  pixQrCode: "00020126580014BR.GOV.BCB.PIX",
  expiresAt: "2026-09-18T00:00:00Z",
};

export const COBRANCA_BOLETO = {
  chargeId: "charge_4",
  method: "BOLETO",
  status: "pending",
  idempotentReplay: false,
  threeDs: null,
  boletoBarcode: "34191.79001 01043.510047 91020.150008 1 96610000019900",
  boletoUrl: "https://paysi.com.br/boletos/charge_4.pdf",
  pixQrCode: null,
  expiresAt: "2026-09-20T00:00:00Z",
};

export async function preencherComprador(page: Page) {
  await page.getByLabel(/nome completo/i).fill("Maria Compradora");
  await page.getByLabel(/e-mail/i).fill("maria@example.com");
  await page.getByLabel(/cpf/i).fill("39053344705");
}

/** CEP, número e telefone do titular: a Asaas exige para tokenizar o cartão. */
export async function preencherTitularDoCartao(page: Page) {
  await page.getByLabel(/cep do titular/i).fill("01310100");
  await page.getByLabel(/número do endereço/i).fill("10");
  await page.getByLabel(/telefone com ddd/i).fill("11999998888");
}

export async function aceitarTermos(page: Page) {
  await page.getByRole("checkbox", { name: /li e aceito os/i }).check();
}
