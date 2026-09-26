import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const ID_ATIVA = "11111111-1111-4111-8111-111111111111";
const ID_TESTE = "33333333-3333-4333-8333-333333333333";

const LINHA_ATIVA = { id: ID_ATIVA, code: "1111111", createdAt: "2026-09-17T20:16:00Z", status: "ACTIVE", cancelPending: false, productName: "Rangu", productId: "p1", offerName: null, cycle: "MONTHLY", buyerName: "VERA LÚCIA & CLEICA", buyerEmail: "lu_verha@hotmail.com", netCents: 4342, nextChargeAt: "2026-10-17T20:16:00Z" };
const LINHA_TESTE = { ...LINHA_ATIVA, id: ID_TESTE, code: "3333333", status: "TRIAL", buyerName: "Ana em teste", buyerEmail: "ana@exemplo.com", netCents: null, offerName: "Plano Pro" };

const DETALHE = {
  id: ID_ATIVA, code: "1111111", status: "ACTIVE", cancelPending: false, type: "PRODUCER", createdAt: "2026-09-17T20:16:00Z", accessUntil: "2026-10-17T20:16:00Z", trialEndsAt: null,
  nextChargeAt: "2026-10-17T20:16:00Z", canceledAt: null, productName: "Rangu", productId: "p1", offerName: null, cycle: "MONTHLY", netCents: 4342, installments: 1, method: "PIX", approvedCharges: 1,
  buyer: { name: "VERA LÚCIA & CLEICA", email: "lu_verha@hotmail.com", phone: "27997509992", taxId: "22247741000104", personType: "PJ", ip: "186.213.115.109" },
  payments: [{ chargeId: "c1", cycleNumber: 1, createdAt: "2026-09-17T20:16:00Z", paidAt: "2026-09-17T20:16:00Z", status: "PAID", netCents: 4342 }],
  canCancel: true,
};

function pagina(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 1, size: 10, total: items.length, totalPages: 1, summary: { activeCount: 1, monthlyRecurringCents: 4342 }, ...extra };
}

async function preparar(page: Page, listagem: (url: URL) => unknown = () => pagina([LINHA_ATIVA])) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [{ id: "p1", name: "Rangu" }, { id: "p2", name: "Outro" }], nextCursor: null } }));
  await page.route("**/api/v1/subscriptions?**", (route) => route.fulfill({ json: listagem(new URL(route.request().url())) }));
  await page.route(`**/api/v1/subscriptions/${ID_ATIVA}/details`, (route) => route.fulfill({ json: DETALHE }));
}

test.describe("assinaturas (visão do vendedor)", () => {
  test("mostra cards, abas, tabela com valor por período e paginação numerada", async ({ page }) => {
    await preparar(page, () => pagina([LINHA_ATIVA, LINHA_TESTE], { total: 25, totalPages: 3 }));
    await page.goto("/assinaturas");
    await expect(page.getByRole("heading", { name: "Assinaturas" })).toBeVisible();
    await expect(page.getByRole("region", { name: "Assinaturas ativas" })).toContainText("1");
    await expect(page.getByRole("region", { name: "Faturamento recorrente mensal" })).toContainText("R$ 43,42");
    await expect(page.getByRole("tab", { name: "Ativas" })).toHaveAttribute("aria-selected", "true");
    for (const coluna of ["Data de início", "Produto", "Cliente", "Status", "Valor líquido"]) {
      await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    }
    await expect(page.getByText("R$ 43,42 / mês")).toBeVisible();
    await expect(page.getByText("lu_verha@hotmail.com")).toBeVisible();
    await expect(page.getByText("Ativo", { exact: true })).toBeVisible();
    await expect(page.getByText("Em teste", { exact: true })).toBeVisible();
    await expect(page.getByText("Exibindo 1 de 3 páginas")).toBeVisible();
  });

  test("abas, busca e página enviam os parâmetros ao servidor", async ({ page }) => {
    const urls: string[] = [];
    await preparar(page, (url) => { urls.push(url.search); return pagina([LINHA_ATIVA], { totalPages: 4, total: 40, page: Number(url.searchParams.get("page") ?? 1) }); });
    await page.goto("/assinaturas");
    await page.getByRole("tab", { name: "Canceladas" }).click();
    await expect.poll(() => urls.at(-1)).toContain("tab=canceled");
    await page.getByRole("tab", { name: "Todas" }).click();
    await expect.poll(() => urls.at(-1)).toContain("tab=all");
    await page.getByRole("button", { name: "Página 2" }).click();
    await expect.poll(() => urls.at(-1)).toContain("page=2");
    await page.getByPlaceholder(/Buscar por cliente/).fill("vera");
    await expect.poll(() => urls.at(-1)).toContain("q=vera");
  });

  test("filtros: período, produto, frequência, método e status", async ({ page }) => {
    const urls: string[] = [];
    await preparar(page, (url) => { urls.push(url.search); return pagina([LINHA_ATIVA]); });
    await page.goto("/assinaturas");
    await page.getByRole("button", { name: /^Filtros/ }).click();
    await page.getByLabel("Início de").fill("2026-09-01");
    await page.getByLabel("Início até").fill("2026-09-30");
    await page.getByRole("combobox", { name: /^Produto/ }).selectOption("p2");
    await page.getByRole("combobox", { name: /^Frequência/ }).selectOption("ANNUAL");
    await page.getByRole("combobox", { name: /^Método/ }).selectOption("CARD");
    await page.getByLabel("Em atraso").check();
    await page.getByRole("button", { name: "Aplicar filtros" }).click();
    await expect.poll(() => urls.at(-1)).toContain("from=2026-09-01");
    const ultima = new URLSearchParams(urls.at(-1));
    expect(ultima.get("productId")).toBe("p2");
    expect(ultima.get("cycle")).toBe("ANNUAL");
    expect(ultima.get("method")).toBe("CARD");
    expect(ultima.getAll("status")).toEqual(["PAST_DUE"]);
    await expect(page.getByRole("button", { name: "Filtros (6)" })).toBeVisible();
  });

  test("painel Ver detalhes: Assinatura, Cliente e Pagamentos com todos os campos", async ({ page }) => {
    await preparar(page);
    await page.goto("/assinaturas");
    await page.getByRole("button", { name: /Ver detalhes da assinatura 1111111/ }).click();
    const painel = page.getByRole("dialog");
    for (const rotulo of ["Data de início", "Status", "Acesso liberado até", "Tipo", "Produto", "Plano", "Valor líquido", "Parcelas", "Frequência", "Cobranças aprovadas", "Método de pagamento", "Próxima cobrança"]) {
      await expect(painel.getByText(rotulo, { exact: true })).toBeVisible();
    }
    await expect(painel.getByText("Sou produtor")).toBeVisible();
    await expect(painel.getByText("Plano Mensal")).toBeVisible();
    await expect(painel.getByText("Mensal", { exact: true })).toBeVisible();
    await expect(painel.getByText("R$ 43,42").first()).toBeVisible();
    await expect(painel.getByText("17/10/2026").first()).toBeVisible();

    await painel.getByRole("tab", { name: "Cliente" }).click();
    await expect(painel.getByText("22.247.741/0001-04")).toBeVisible();
    await expect(painel.getByText("CNPJ", { exact: true })).toBeVisible();
    await expect(painel.getByText("+55 27 99750-9992")).toBeVisible();
    await expect(painel.getByRole("link", { name: "Conversar no WhatsApp" })).toHaveAttribute("href", "https://wa.me/5527997509992");
    await expect(painel.getByText("186.213.115.109")).toBeVisible();

    await painel.getByRole("tab", { name: "Pagamentos" }).click();
    await expect(painel.getByRole("columnheader", { name: "Data" })).toBeVisible();
    await expect(painel.getByText("Pago", { exact: true })).toBeVisible();
    await expect(painel.getByText("Ciclo 1")).toBeVisible();
  });

  test("cancelar assinatura pelo menu agenda o cancelamento", async ({ page }) => {
    await preparar(page);
    let chamadas = 0;
    await page.route(`**/api/v1/subscriptions/${ID_ATIVA}/cancel`, (route) => { chamadas += 1; return route.fulfill({ status: 204 }); });
    await page.goto("/assinaturas");
    await page.getByRole("button", { name: /Ver detalhes da assinatura 1111111/ }).click();
    await page.getByRole("button", { name: "Mais ações da assinatura" }).click();
    await page.getByRole("menuitem", { name: "Cancelar assinatura" }).click();
    await expect(page.getByText(/Não haverá novas cobranças/)).toBeVisible();
    await page.getByRole("button", { name: "Confirmar cancelamento" }).click();
    await expect(page.getByText(/Cancelamento agendado\. O cliente mantém o acesso/)).toBeVisible();
    expect(chamadas).toBe(1);
  });

  test("assinatura já cancelada não oferece cancelar de novo", async ({ page }) => {
    await preparar(page);
    await page.route(`**/api/v1/subscriptions/${ID_ATIVA}/details`, (route) => route.fulfill({ json: { ...DETALHE, status: "CANCELED", canCancel: false, accessUntil: null, canceledAt: "2026-09-20T12:00:00Z" } }));
    await page.goto("/assinaturas");
    await page.getByRole("button", { name: /Ver detalhes da assinatura 1111111/ }).click();
    await page.getByRole("button", { name: "Mais ações da assinatura" }).click();
    await expect(page.getByRole("menuitem", { name: "Cancelar assinatura" })).toBeDisabled();
    await expect(page.getByText("Esta assinatura já foi cancelada.")).toBeVisible();
  });

  test("exportar baixa o CSV e avisa", async ({ page }) => {
    await preparar(page);
    let pedida = "";
    await page.route("**/api/v1/subscriptions/export?**", (route) => { pedida = route.request().url(); return route.fulfill({ status: 200, contentType: "text/csv; charset=utf-8", body: "﻿ID da assinatura;Data de início\r\n1111111;17/09/2026 17:16\r\n" }); });
    await page.goto("/assinaturas");
    const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Exportar" }).click()]);
    expect(download.suggestedFilename()).toMatch(/^assinaturas-paysi-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(pedida).toContain("tab=active");
    await expect(page.getByText("Exportação iniciada com sucesso.")).toBeVisible();
  });

  test("estado vazio e erro ao carregar", async ({ page }) => {
    await preparar(page, () => pagina([], { summary: { activeCount: 0, monthlyRecurringCents: 0 } }));
    await page.goto("/assinaturas");
    await expect(page.getByText("Nenhuma assinatura encontrada")).toBeVisible();
    await expect(page.getByRole("region", { name: "Assinaturas ativas" })).toContainText("0");
  });

  test("falha ao carregar mostra mensagem com nova tentativa", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
    await page.route("**/api/v1/subscriptions?**", (route) => route.fulfill({ status: 500, json: { code: "ERRO", message: "falhou" } }));
    await page.goto("/assinaturas");
    await expect(page.getByText(/não foi possível carregar as assinaturas/i)).toBeVisible();
    await expect(page.getByRole("button", { name: "Tentar novamente" })).toBeVisible();
  });

  test("passa no axe com a lista e com o painel aberto", async ({ page }) => {
    await preparar(page);
    await page.goto("/assinaturas");
    await expect(page.getByText("lu_verha@hotmail.com")).toBeVisible();
    let axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), "lista").toEqual([]);
    await page.getByRole("button", { name: /Ver detalhes da assinatura 1111111/ }).click();
    await expect(page.getByRole("tab", { name: "Pagamentos" })).toBeVisible();
    axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), "detalhe").toEqual([]);
  });
});
