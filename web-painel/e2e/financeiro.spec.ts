import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const OVERVIEW = {
  holder: { name: "Guilherme Rodrigues Galdino", personType: "PF", taxId: "16703691703", country: "BR" },
  balance: { availableCents: 8367, pendingCents: 4985, reserveCents: 500, debtCents: 0 },
  pix: { bankAccountId: "bank_1", keyType: "CPF", key: "16703691703", verifiedAt: "2026-09-01T12:00:00Z" },
  kycStatus: "APPROVED", mfaEnabled: false, payoutFeeCents: 0, minPayoutCents: 200, mfaThresholdCents: 100000,
};
const SAQUE_OK = { id: "p1", createdAt: "2026-09-21T12:00:00Z", amountCents: 5558, status: "CONFIRMED", destinationName: "Guilherme Rodrigues Galdino", pixKeyType: "CPF", pixKey: "16703691703", receiptUrl: null };
const SAQUE_PROC = { ...SAQUE_OK, id: "p2", createdAt: "2026-09-26T12:00:00Z", amountCents: 8000, status: "SENT" };
const FEES = { plan: "TRANSACIONAL", methods: [{ method: "PIX", feeBps: 399, fixedCents: 200 }, { method: "BOLETO", feeBps: 399, fixedCents: 200 }, { method: "CARD_1", feeBps: 599, fixedCents: 200 }, { method: "CARD_6", feeBps: 649, fixedCents: 200 }, { method: "CARD_12", feeBps: 699, fixedCents: 200 }], payoutDelayDays: 32, reserveBps: 400, reserveDays: 90 };

async function preparar(page: Page, overview: Record<string, unknown> = OVERVIEW, saques: unknown[] = [SAQUE_PROC, SAQUE_OK]) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/accounts/me/finance", (route) => route.fulfill({ json: overview }));
  await page.route("**/api/v1/accounts/me/payouts?**", (route) => route.fulfill({ json: { items: saques, page: 1, size: 10, total: saques.length, totalPages: 1 } }));
  await page.route("**/api/v1/accounts/me/fees", (route) => route.fulfill({ json: FEES }));
}

test.describe("financeiro", () => {
  test("mostra os saldos, as abas e o histórico de saques com status", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo");
    await expect(page.getByRole("heading", { name: "Financeiro" })).toBeVisible();
    await expect(page.getByRole("region", { name: "Saldo disponível" })).toContainText("R$ 83,67");
    await expect(page.getByRole("region", { name: "Saldo pendente" })).toContainText("R$ 49,85");
    for (const aba of ["Saques", "Extrato", "Dados bancários", "Taxas e Prazos", "Identidade"]) {
      await expect(page.getByRole("tab", { name: aba })).toBeVisible();
    }
    await expect(page.getByRole("columnheader", { name: "Valor" })).toBeVisible();
    await expect(page.getByText("Processando")).toBeVisible();
    await expect(page.getByText("Sucesso")).toBeVisible();
    await expect(page.getByRole("button", { name: "Alterar minha conta para CNPJ" })).toBeVisible();
  });

  test("Ver detalhes do saque: valor, data, destino, chave Pix e status", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo");
    await page.getByRole("button", { name: /Ver detalhes do saque de 21\/09\/2026/ }).click();
    const painel = page.getByRole("dialog");
    await expect(painel.getByText("R$ 55,58")).toBeVisible();
    await expect(painel.getByText("Guilherme Rodrigues Galdino")).toBeVisible();
    await expect(painel.getByText("167.036.917-03")).toBeVisible();
    await expect(painel.getByText("Sucesso")).toBeVisible();
    for (const rotulo of ["Valor", "Data", "Destino", "Chave PIX", "Status"]) await expect(painel.getByText(rotulo, { exact: true })).toBeVisible();
  });

  test("Efetuar saque abaixo do limite de segurança não pede código", async ({ page }) => {
    await preparar(page);
    let corpo: Record<string, unknown> | null = null;
    await page.route("**/api/v1/accounts/me/payouts", async (route) => {
      if (route.request().method() !== "POST") return route.fallback();
      corpo = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: { payoutId: "p3", status: "SENT", receiptUrl: null, idempotentReplay: false } });
    });
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Efetuar saque" }).click();
    const janela = page.getByRole("dialog", { name: "Realizar saque" });
    await expect(janela.getByText("Disponível: R$ 83,67")).toBeVisible();
    await expect(janela.getByText("167.036.917-03")).toBeVisible();
    await expect(janela.getByText("Saque sem taxa.")).toBeVisible();
    await janela.getByLabel("Valor do saque em reais").fill("80,00");
    await janela.getByRole("button", { name: "Confirmar" }).click();
    await expect(page.getByText("Solicitação de saque efetuada")).toBeVisible();
    expect(corpo).toMatchObject({ amountCents: 8000, bankAccountId: "bank_1", mfaChallengeId: null });
  });

  test("saque valida mínimo e saldo disponível", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Efetuar saque" }).click();
    const janela = page.getByRole("dialog", { name: "Realizar saque" });
    await janela.getByLabel("Valor do saque em reais").fill("1,00");
    await janela.getByRole("button", { name: "Confirmar" }).click();
    await expect(janela.getByText(/saque mínimo é de R\$\s2,00/)).toBeVisible();
    await janela.getByLabel("Valor do saque em reais").fill("999,00");
    await janela.getByRole("button", { name: "Confirmar" }).click();
    await expect(janela.getByText("O valor é maior que o saldo disponível.")).toBeVisible();
  });

  test("saque acima do limite pede o código de segurança e o envia junto", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, balance: { ...OVERVIEW.balance, availableCents: 500000 }, mfaEnabled: true });
    let corpo: Record<string, unknown> | null = null;
    await page.route("**/api/v1/accounts/me/payouts", async (route) => {
      if (route.request().method() !== "POST") return route.fallback();
      corpo = route.request().postDataJSON();
      return route.fulfill({ status: 201, json: { payoutId: "p4", status: "SENT", receiptUrl: null, idempotentReplay: false } });
    });
    await page.route("**/api/v1/mfa/challenges", (route) => route.fulfill({ status: 201, json: { challengeId: "mfa_1", operation: "PAYOUT", expiresAt: "2026-09-26T12:05:00Z", verified: false } }));
    await page.route("**/api/v1/mfa/challenges/mfa_1/verify", (route) => route.fulfill({ json: { challengeId: "mfa_1", operation: "PAYOUT", expiresAt: "2026-09-26T12:05:00Z", verified: true } }));
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Efetuar saque" }).click();
    await page.getByLabel("Valor do saque em reais").fill("2000,00");
    await page.getByRole("dialog", { name: "Realizar saque" }).getByRole("button", { name: "Confirmar" }).click();
    await expect(page.getByRole("dialog", { name: "Confirmação de segurança" })).toBeVisible();
    await page.getByLabel(/código/i).fill("123456");
    await page.getByRole("button", { name: /confirmar/i }).last().click();
    await expect(page.getByText("Solicitação de saque efetuada")).toBeVisible();
    expect(corpo).toMatchObject({ amountCents: 200000, mfaChallengeId: "mfa_1" });
  });

  test("sem chave Pix o saque leva aos Dados bancários", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, pix: null });
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Efetuar saque" }).click();
    await page.getByRole("button", { name: "Ir para Dados bancários" }).click();
    await expect(page).toHaveURL(/aba=dados/);
    await expect(page.getByRole("heading", { name: "Dados bancários" })).toBeVisible();
  });

  test("Dados bancários: titular, documento, país e primeira chave Pix sem código", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, pix: null });
    let corpo: Record<string, unknown> | null = null;
    await page.route("**/api/v1/accounts/me/pix-key", async (route) => { corpo = route.request().postDataJSON(); return route.fulfill({ json: { bankAccountId: "b", keyType: "CPF", key: "16703691703" } }); });
    await page.goto("/saldo?aba=dados");
    await expect(page.getByText("Guilherme Rodrigues Galdino").first()).toBeVisible();
    await expect(page.getByText(/CPF - 167\.036\.917-03/)).toBeVisible();
    await expect(page.getByLabel("País")).toBeDisabled();
    await expect(page.getByText(/a chave Pix deve pertencer ao CPF \(167\.036\.917-03\)/)).toBeVisible();
    await page.getByLabel("Chave Pix").fill("167.036.917-03");
    await page.getByRole("button", { name: "Salvar alterações" }).click();
    await expect(page.getByText("Chave Pix salva.")).toBeVisible();
    expect(corpo).toMatchObject({ pixKey: "167.036.917-03" });
  });

  test("trocar a chave sem duas etapas ativas leva à ativação; com ativas pede o código", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/mfa/setup", (route) => route.fulfill({ status: 200, json: { secret: "ABCD EFGH IJKL", otpauthUri: "otpauth://totp/Paysi:x?secret=ABCDEFGHIJKL", recoveryCodes: ["AAAA-1111", "BBBB-2222"] } }));
    let confirmado = false;
    await page.route("**/api/v1/mfa/setup/confirm", (route) => { confirmado = true; return route.fulfill({ status: 204 }); });
    await page.goto("/saldo?aba=dados");
    await page.getByLabel("Chave Pix").fill("maria@exemplo.com");
    await page.getByRole("button", { name: "Salvar alterações" }).click();
    const janela = page.getByRole("dialog", { name: "Ativar verificação em duas etapas" });
    await expect(janela.getByText("ABCD EFGH IJKL")).toBeVisible();
    await expect(janela.getByText("AAAA-1111")).toBeVisible();
    await janela.getByLabel("Guardei os códigos de recuperação").check();
    await janela.getByLabel("Código de 6 dígitos").fill("123456");
    await janela.getByRole("button", { name: "Ativar" }).click();
    await expect(page.getByText("Verificação em duas etapas ativada.")).toBeVisible();
    expect(confirmado).toBe(true);
  });

  test("Alterar minha conta para CNPJ: dados, código de segurança e reinício da verificação", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, mfaEnabled: true });
    let corpo: Record<string, unknown> | null = null;
    await page.route("**/api/v1/accounts/me/convert-to-company", async (route) => { corpo = route.request().postDataJSON(); return route.fulfill({ status: 200, json: { bankAccountId: "b2" } }); });
    await page.route("**/api/v1/mfa/challenges", (route) => route.fulfill({ status: 201, json: { challengeId: "mfa_2", operation: "BANK_ACCOUNT_CHANGE", expiresAt: "2026-09-26T12:05:00Z", verified: false } }));
    await page.route("**/api/v1/mfa/challenges/mfa_2/verify", (route) => route.fulfill({ json: { challengeId: "mfa_2", operation: "BANK_ACCOUNT_CHANGE", expiresAt: "2026-09-26T12:05:00Z", verified: true } }));
    await page.route("**/api/v1/accounts/me", (route) => route.fulfill({ json: { accountId: "a", kycStatus: "PENDING", providerUrl: null, requirements: [] } }));
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Alterar minha conta para CNPJ" }).click();
    const janela = page.getByRole("dialog", { name: "Alterar minha conta para CNPJ" });
    await expect(janela.getByText("irreversível")).toBeVisible();
    await expect(janela.getByText("167.036.917-03")).toBeVisible();
    await janela.getByRole("button", { name: "Continuar" }).click();
    await expect(janela.getByText("Informe a razão social.")).toBeVisible();
    await janela.getByLabel("Razão Social").fill("Empresa Exemplo LTDA");
    await janela.getByLabel("CNPJ", { exact: true }).fill("11222333000181");
    await expect(janela.getByLabel("CNPJ", { exact: true })).toHaveValue("11.222.333/0001-81");
    await janela.getByLabel("Chave Pix").fill("11222333000181");
    await janela.getByRole("button", { name: "Continuar" }).click();
    await page.getByLabel(/código/i).fill("123456");
    await page.getByRole("button", { name: /confirmar/i }).last().click();
    await expect(page.getByText(/Conta alterada para CNPJ/)).toBeVisible();
    await expect(page).toHaveURL(/aba=identidade/);
    expect(corpo).toMatchObject({ legalName: "Empresa Exemplo LTDA", cnpj: "11.222.333/0001-81", pixKey: "11222333000181" });
  });

  test("conta PJ não oferece alterar para CNPJ", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, holder: { ...OVERVIEW.holder, personType: "PJ", taxId: "11222333000181" } });
    await page.goto("/saldo");
    await expect(page.getByRole("button", { name: "Alterar minha conta para CNPJ" })).toHaveCount(0);
    await page.getByRole("tab", { name: "Dados bancários" }).click();
    await expect(page.getByText(/CNPJ - 11\.222\.333\/0001-81/)).toBeVisible();
  });

  test("Taxas e Prazos: taxas por forma de pagamento, prazo e reserva", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo?aba=taxas");
    await expect(page.getByRole("heading", { name: "Vendas para o Brasil" })).toBeVisible();
    await expect(page.getByText(/Pix:.*3,99%.*R\$\s2,00 por venda aprovada/)).toBeVisible();
    await expect(page.getByText(/Cartão de crédito à vista:.*5,99%/)).toBeVisible();
    await expect(page.getByText("32 dias")).toBeVisible();
    await expect(page.getByText(/4%.*de cada venda fica reservado por.*90 dias/)).toBeVisible();
  });

  test("Identidade: verificada mostra 'Você já pode vender!'", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/accounts/me", (route) => route.fulfill({ json: { accountId: "a", kycStatus: "APPROVED", providerUrl: null, requirements: [] } }));
    await page.goto("/saldo?aba=identidade");
    await expect(page.getByRole("heading", { name: "Verifique a sua identidade" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Identidade verificada" })).toBeVisible();
    await expect(page.getByText("Você já pode vender!")).toBeVisible();
  });

  test("Identidade pendente mostra o passo a passo e inicia a verificação", async ({ page }) => {
    await preparar(page, { ...OVERVIEW, kycStatus: "PENDING" });
    let iniciou = false;
    await page.route("**/api/v1/accounts/me", (route) => route.fulfill({ json: { accountId: "a", kycStatus: "PENDING", providerUrl: null, requirements: [{ code: "DOC", label: "Documento com foto", status: "PENDING", reason: null, estimatedAt: null }] } }));
    await page.route("**/api/v1/accounts/me/kyc", (route) => { iniciou = true; return route.fulfill({ status: 200, json: { accountId: "a", kycStatus: "SUBMITTED", providerUrl: null, requirements: [] } }); });
    await page.goto("/saldo?aba=identidade");
    await expect(page.getByText("Documento com foto")).toBeVisible();
    await page.getByRole("button", { name: "Iniciar verificação" }).click();
    await expect.poll(() => iniciou).toBe(true);
  });

  test("rotas antigas redirecionam para o Financeiro", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo/conta-bancaria");
    await expect(page).toHaveURL(/\/saldo\?aba=dados/);
    await page.goto("/verificacao");
    await expect(page).toHaveURL(/\/saldo\?aba=identidade/);
    await page.goto("/saldo/sacar");
    await expect(page.getByRole("dialog", { name: "Realizar saque" })).toBeVisible();
  });

  test("o menu não tem mais o item Verificação", async ({ page }) => {
    await preparar(page);
    await page.goto("/saldo");
    await expect(page.getByRole("navigation", { name: "Navegação principal" }).getByRole("link", { name: "Verificação" })).toHaveCount(0);
    await expect(page.getByRole("navigation", { name: "Navegação principal" }).getByRole("link", { name: "Financeiro" })).toBeVisible();
  });

  test("passa no axe nas abas e com o painel de saque aberto", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/accounts/me", (route) => route.fulfill({ json: { accountId: "a", kycStatus: "APPROVED", providerUrl: null, requirements: [] } }));
    for (const aba of ["", "?aba=dados", "?aba=taxas", "?aba=identidade"]) {
      await page.goto(`/saldo${aba}`);
      await expect(page.getByRole("tab", { name: "Saques" })).toBeVisible();
      await page.waitForTimeout(400);
      const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
      expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), aba || "saques").toEqual([]);
    }
    await page.goto("/saldo");
    await page.getByRole("button", { name: "Efetuar saque" }).click();
    await expect(page.getByRole("dialog", { name: "Realizar saque" })).toBeVisible();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), "saque").toEqual([]);
  });
});
