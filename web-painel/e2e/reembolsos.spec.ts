import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const ID_VENDA = "11111111-1111-4111-8111-111111111111";

const REEMBOLSO_VENDEDOR = { id: "r1", chargeId: ID_VENDA, saleCode: "1111111", productName: "Cartilha do Aprovado PMES Completo", buyerName: "fulano", buyerEmail: "fulano@gmail.com", buyerPhone: "27996936745", amountCents: 4700, sellerCents: 4075, reason: null, status: "SUCCEEDED", requestedBy: "SELLER", createdAt: "2026-09-21T15:00:00Z", settledAt: "2026-09-21T15:01:00Z" };
const REEMBOLSO_COMPRADOR = { ...REEMBOLSO_VENDEDOR, id: "r2", chargeId: "22222222-2222-4222-8222-222222222222", saleCode: "2222222", buyerName: "Ewerthon carvalho dos Santos", buyerEmail: "ewerthonsantos12@gmail.com", buyerPhone: null, reason: "Preciso do dinheiro para outras coisas", requestedBy: "BUYER", createdAt: "2026-08-18T15:00:00Z" };
const REEMBOLSO_PAYSI = { ...REEMBOLSO_VENDEDOR, id: "r3", saleCode: "3333333", buyerName: "Natiele", buyerEmail: "natielesouza2707@gmail.com", requestedBy: "ADMIN", sellerCents: 2235, amountCents: 2700, createdAt: "2026-07-17T15:00:00Z" };

function pagina(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 1, size: 10, total: items.length, totalPages: 1, ...extra };
}

async function preparar(page: Page, listagem: (url: URL) => unknown = () => pagina([REEMBOLSO_VENDEDOR, REEMBOLSO_COMPRADOR, REEMBOLSO_PAYSI], { total: 21, totalPages: 3 })) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/refunds?**", (route) => route.fulfill({ json: listagem(new URL(route.request().url())) }));
}

test.describe("reembolsos", () => {
  test("mostra as colunas da Kiwify, autores e paginação", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas/reembolsos");
    await expect(page.getByRole("heading", { name: "Reembolsos" })).toBeVisible();
    for (const coluna of ["Solicitação", "Comprador", "Status", "Autor", "Valor líquido"]) {
      await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    }
    await expect(page.getByText("R$ 40,75").first()).toBeVisible();
    await expect(page.getByText("Vendedor", { exact: true })).toBeVisible();
    await expect(page.getByText("Comprador", { exact: true }).nth(1)).toBeVisible();
    await expect(page.getByText("Paysi", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Reembolsado", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Exibindo 1 de 3 páginas")).toBeVisible();
  });

  test("filtros enviam status, autor (Paysi = ADMIN e SYSTEM) e período", async ({ page }) => {
    const urls: string[] = [];
    await preparar(page, (url) => { urls.push(url.search); return pagina([REEMBOLSO_VENDEDOR]); });
    await page.goto("/vendas/reembolsos");
    await page.getByRole("button", { name: /^Filtros/ }).click();
    await page.getByLabel("De", { exact: true }).fill("2026-07-01");
    await page.getByLabel("Até", { exact: true }).fill("2026-07-31");
    await page.getByLabel("Reembolsado").check();
    await page.getByLabel("Paysi").check();
    await page.getByRole("button", { name: "Aplicar filtros" }).click();
    await expect.poll(() => urls.at(-1)).toContain("from=2026-07-01");
    const ultima = new URLSearchParams(urls.at(-1));
    expect(ultima.getAll("status")).toEqual(["SUCCEEDED"]);
    expect(ultima.getAll("origin")).toEqual(["ADMIN", "SYSTEM"]);
    expect(ultima.get("to")).toBe("2026-07-31");
    await expect(page.getByRole("button", { name: "Filtros (5)" })).toBeVisible();
  });

  test("painel Detalhes da solicitação: abas Detalhes e Comprador", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas/reembolsos");
    await page.getByRole("button", { name: "Ver solicitação de reembolso de fulano" }).click();
    const painel = page.getByRole("dialog");
    await expect(painel.getByRole("heading", { name: "Detalhes da solicitação" })).toBeVisible();
    await expect(painel.getByText("Reembolsado", { exact: true })).toBeVisible();
    await expect(painel.getByText("21/09/2026").first()).toBeVisible();
    await expect(painel.getByRole("link", { name: /Ver venda 1111111/ })).toHaveAttribute("href", `/vendas?venda=${ID_VENDA}`);
    await expect(painel.getByText("Cartilha do Aprovado PMES Completo")).toBeVisible();
    await expect(painel.getByText("R$ 40,75")).toBeVisible();
    await expect(painel.getByText("Vendedor", { exact: true })).toBeVisible();
    await expect(painel.getByText("------")).toBeVisible();

    await painel.getByRole("tab", { name: "Comprador" }).click();
    await expect(painel.getByText("fulano@gmail.com")).toBeVisible();
    await expect(painel.getByText("+55 27 99693-6745")).toBeVisible();
    await expect(painel.getByRole("link", { name: "Conversar no WhatsApp" })).toHaveAttribute("href", "https://wa.me/5527996936745");
  });

  test("motivo do comprador aparece e telefone ausente diz Não informado", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas/reembolsos");
    await page.getByRole("button", { name: "Ver solicitação de reembolso de Ewerthon carvalho dos Santos" }).click();
    const painel = page.getByRole("dialog");
    await expect(painel.getByText("Preciso do dinheiro para outras coisas")).toBeVisible();
    await painel.getByRole("tab", { name: "Comprador" }).click();
    await expect(painel.getByText("Não informado")).toBeVisible();
  });

  test("Ver venda abre a venda no painel de detalhes", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/sales?**", (route) => route.fulfill({ json: { items: [], page: 1, size: 10, total: 0, totalPages: 1, summary: { count: 0, netCents: 0 } } }));
    await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [], nextCursor: null } }));
    await page.route(`**/api/v1/sales/${ID_VENDA}`, (route) => route.fulfill({ json: {
      id: ID_VENDA, code: "1111111", status: "REFUNDED", type: "PRODUCER", productName: "Cartilha do Aprovado PMES Completo", productId: "p1", offerName: null, method: "PIX", installments: 1,
      createdAt: "2026-09-01T12:00:00Z", approvedAt: "2026-09-01T12:01:00Z", availableAt: "2026-09-08T12:01:00Z", reference: null, cycleNumber: null, subscriptionId: null, couponCode: null,
      buyer: { name: "fulano", email: "fulano@gmail.com", phone: null, taxId: "16573709764", personType: "PF", ip: null },
      amounts: { basePriceCents: 4700, discountCents: 0, paidCents: 4700, feesCents: 625, affiliateCents: 0, sellerCents: 4075, refundedCents: 4700, netCents: 0 },
      split: [{ name: "Eu", role: "SELLER", amountCents: 4075 }], payoutState: "REFUNDED", canRefund: false, refunds: [] } }));
    await page.goto("/vendas/reembolsos");
    await page.getByRole("button", { name: "Ver solicitação de reembolso de fulano" }).click();
    await page.getByRole("link", { name: /Ver venda 1111111/ }).click();
    await expect(page).toHaveURL(new RegExp(`/vendas\\?venda=${ID_VENDA}`));
    await expect(page.getByRole("dialog").getByText("Devolvido ao comprador").or(page.getByRole("dialog").getByText("1111111"))).toBeVisible();
  });

  test("exportar mostra 'Exportação iniciada com sucesso.' e baixa o CSV", async ({ page }) => {
    await preparar(page);
    let pedida = "";
    await page.route("**/api/v1/refunds/export?**", (route) => { pedida = route.request().url(); return route.fulfill({ status: 200, contentType: "text/csv; charset=utf-8", body: "﻿Solicitação;ID da venda\r\n21/09/2026 12:00;1111111\r\n" }); });
    await page.goto("/vendas/reembolsos");
    const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Exportar" }).click()]);
    expect(download.suggestedFilename()).toMatch(/^reembolsos-paysi-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(pedida).toContain("/api/v1/refunds/export");
    await expect(page.getByText("Exportação iniciada com sucesso.")).toBeVisible();
  });

  test("estado vazio e acessibilidade", async ({ page }) => {
    await preparar(page, () => pagina([], { total: 0 }));
    await page.goto("/vendas/reembolsos");
    await expect(page.getByText("Nenhum reembolso")).toBeVisible();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious")).toEqual([]);
  });

  test("passa no axe com o painel de detalhes aberto", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas/reembolsos");
    await page.getByRole("button", { name: "Ver solicitação de reembolso de fulano" }).click();
    await expect(page.getByRole("tab", { name: "Comprador" })).toBeVisible();
    const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious")).toEqual([]);
  });
});
