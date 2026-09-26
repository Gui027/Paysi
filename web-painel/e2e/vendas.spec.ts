import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const ID_PAGO = "11111111-1111-4111-8111-111111111111";
const ID_PENDENTE = "22222222-2222-4222-8222-222222222222";

const LINHA_PAGA = { id: ID_PAGO, code: "1111111", createdAt: "2026-09-25T14:42:00Z", approvedAt: "2026-09-25T14:43:00Z", status: "PAID", productName: "Cartilha do Aprovado PMES", productId: "p1", offerName: null, method: "PIX", installments: 1, buyerName: "Henrique Viana da Silva", buyerEmail: "henriquesjv@hotmail.com", netCents: 2235 };
const LINHA_PENDENTE = { ...LINHA_PAGA, id: ID_PENDENTE, code: "2222222", approvedAt: null, status: "PENDING", productName: "Pacote Mini Simulados PMES", productId: "p2", netCents: 662 };

const DETALHE_PAGO = {
  id: ID_PAGO, code: "1111111", status: "PAID", type: "PRODUCER", productName: "Cartilha do Aprovado PMES", productId: "p1", offerName: "Oferta 1",
  method: "PIX", installments: 1, createdAt: "2026-09-25T14:42:00Z", approvedAt: "2026-09-25T14:43:00Z", availableAt: "2099-10-02T14:43:00Z", reference: "user_123",
  cycleNumber: null, subscriptionId: null, couponCode: "PROMO10",
  buyer: { name: "Henrique Viana da Silva", email: "henriquesjv@hotmail.com", phone: "27999513505", taxId: "16573709764", personType: "PF", ip: "2804:40a8:22b:7a00::1" },
  amounts: { basePriceCents: 990, discountCents: 0, paidCents: 990, feesCents: 328, affiliateCents: 0, sellerCents: 662, refundedCents: 0, netCents: 662 },
  split: [{ name: "Guilherme Rodrigues Galdino", role: "SELLER", amountCents: 662 }],
  payoutState: "TO_RELEASE", canRefund: true, refunds: [],
};

function pagina(items: unknown[], extra: Record<string, unknown> = {}) {
  return { items, page: 1, size: 10, total: items.length, totalPages: 1, summary: { count: items.length, netCents: 2235 }, ...extra };
}

async function preparar(page: Page, listagem: (url: URL) => unknown = () => pagina([LINHA_PAGA])) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [{ id: "p1", name: "Cartilha do Aprovado PMES" }, { id: "p2", name: "Pacote Mini Simulados PMES" }], nextCursor: null } }));
  await page.route("**/api/v1/sales?**", (route) => route.fulfill({ json: listagem(new URL(route.request().url())) }));
  await page.route(`**/api/v1/sales/${ID_PAGO}`, (route) => route.fulfill({ json: DETALHE_PAGO }));
}

test.describe("vendas", () => {
  test("mostra resumo, abas, tabela e a paginação numerada", async ({ page }) => {
    await preparar(page, () => pagina([LINHA_PAGA, LINHA_PENDENTE], { total: 326, totalPages: 33, summary: { count: 326, netCents: 467086 } }));
    await page.goto("/vendas");
    await expect(page.getByRole("heading", { name: "Vendas" })).toBeVisible();
    await expect(page.getByRole("region", { name: "Vendas encontradas" })).toContainText("326");
    await expect(page.getByRole("region", { name: "Valor líquido" })).toContainText("4.670,86");
    await expect(page.getByRole("tab", { name: "Aprovadas" })).toHaveAttribute("aria-selected", "true");
    await expect(page.getByText("Henrique Viana da Silva").first()).toBeVisible();
    await expect(page.getByText("henriquesjv@hotmail.com").first()).toBeVisible();
    await expect(page.getByText("Aguardando pagamento")).toBeVisible();
    await expect(page.getByText("Exibindo 1 de 33 páginas")).toBeVisible();
    await expect(page.getByRole("button", { name: "Página 33" })).toBeVisible();
  });

  test("troca de página, de aba e busca enviam os parâmetros ao servidor", async ({ page }) => {
    const urls: string[] = [];
    await preparar(page, (url) => { urls.push(url.search); return pagina([LINHA_PAGA], { totalPages: 5, total: 50, page: Number(url.searchParams.get("page") ?? 1) }); });
    await page.goto("/vendas");
    await page.getByRole("button", { name: "Página 3" }).click();
    await expect.poll(() => urls.at(-1)).toContain("page=3");
    await page.getByRole("tab", { name: "Todas" }).click();
    await expect.poll(() => urls.at(-1)).toMatch(/tab=all.*page=1|page=1.*tab=all/);
    await page.getByPlaceholder(/Buscar por cliente/).fill("maria");
    await expect.poll(() => urls.at(-1)).toContain("q=maria");
  });

  test("filtros: período, produto, método e status (só na aba Todas)", async ({ page }) => {
    const urls: string[] = [];
    await preparar(page, (url) => { urls.push(url.search); return pagina([LINHA_PAGA]); });
    await page.goto("/vendas");
    await page.getByRole("tab", { name: "Todas" }).click();
    await page.getByRole("button", { name: /^Filtros/ }).click();
    await page.getByLabel("De", { exact: true }).fill("2026-09-01");
    await page.getByLabel("Até", { exact: true }).fill("2026-09-30");
    await page.getByRole("combobox", { name: /^Produto/ }).selectOption("p2");
    await page.getByLabel("Método de pagamento").selectOption("PIX");
    await page.getByLabel("Aguardando pagamento").check();
    await page.getByRole("button", { name: "Aplicar filtros" }).click();
    await expect.poll(() => urls.at(-1)).toContain("from=2026-09-01");
    const ultima = new URLSearchParams(urls.at(-1));
    expect(ultima.get("to")).toBe("2026-09-30");
    expect(ultima.get("productId")).toBe("p2");
    expect(ultima.get("method")).toBe("PIX");
    expect(ultima.getAll("status")).toEqual(["PENDING"]);
    await expect(page.getByRole("button", { name: "Filtros (5)" })).toBeVisible();
    await page.getByRole("button", { name: /^Filtros/ }).click();
    await page.getByRole("button", { name: "Limpar filtros" }).click();
    await expect.poll(() => urls.at(-1)).not.toContain("from=");
  });

  test("painel Ver detalhes: abas Venda, Cliente e Valores com todos os campos", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas");
    await page.getByRole("button", { name: /Ver detalhes da venda 1111111/ }).click();
    const painel = page.getByRole("dialog");
    await expect(painel.getByRole("heading", { name: "Ver detalhes" })).toBeVisible();
    await expect(painel.getByText("1111111")).toBeVisible();
    await expect(painel.getByText("Sou produtor")).toBeVisible();
    await expect(painel.getByText("Pago", { exact: true })).toBeVisible();
    await expect(painel.getByText("R$ 6,62").first()).toBeVisible();
    await expect(painel.getByText("Método de pagamento")).toBeVisible();
    await expect(painel.getByText("Parcelas")).toBeVisible();
    await expect(painel.getByText("Data da criação")).toBeVisible();
    await expect(painel.getByText("Data da aprovação")).toBeVisible();
    await expect(painel.getByText("PROMO10")).toBeVisible();
    await expect(painel.getByText("user_123")).toBeVisible();

    await painel.getByRole("tab", { name: "Cliente" }).click();
    await expect(painel.getByText("165.737.097-64")).toBeVisible();
    await expect(painel.getByText("+55 27 99951-3505")).toBeVisible();
    await expect(painel.getByRole("link", { name: "Conversar no WhatsApp" })).toHaveAttribute("href", "https://wa.me/5527999513505");
    await expect(painel.getByText("2804:40a8:22b:7a00::1")).toBeVisible();
    await expect(painel.getByText("henriquesjv@hotmail.com")).toBeVisible();

    await painel.getByRole("tab", { name: "Valores" }).click();
    await expect(painel.getByText("Preço base do produto")).toBeVisible();
    await expect(painel.getByText("R$ 9,90").first()).toBeVisible();
    await expect(painel.getByText("R$ 3,28")).toBeVisible();
    await expect(painel.getByText("Guilherme Rodrigues Galdino")).toBeVisible();
    await expect(painel.getByText("Seu recebimento")).toBeVisible();
    await expect(painel.getByText(/A liberar em/)).toBeVisible();
  });

  test("reembolso total e parcial pelo menu da venda", async ({ page }) => {
    await preparar(page);
    const corpos: Record<string, unknown>[] = [];
    let chaves: string[] = [];
    await page.route("**/api/v1/charges/*/refunds", async (route) => {
      corpos.push(route.request().postDataJSON());
      chaves.push(route.request().headers()["idempotency-key"] ?? "");
      await route.fulfill({ status: 201, json: { refundId: "r1", status: "SUCCEEDED", chargeStatus: "PARTIALLY_REFUNDED", chargeRefundedCents: 500 } });
    });
    await page.goto("/vendas");
    await page.getByRole("button", { name: /Ver detalhes da venda 1111111/ }).click();
    await page.getByRole("button", { name: "Mais ações da venda" }).click();
    await page.getByRole("menuitem", { name: "Reembolsar venda" }).click();

    await page.getByLabel("Valor parcial").check();
    await page.getByLabel("Valor a reembolsar em reais").fill("abc");
    await page.getByRole("button", { name: "Confirmar reembolso" }).click();
    await expect(page.getByText(/Informe o valor a reembolsar/)).toBeVisible();
    expect(corpos).toHaveLength(0);

    await page.getByLabel("Valor a reembolsar em reais").fill("5,00");
    await page.getByLabel("Motivo (opcional)").fill("Cliente desistiu");
    await page.getByRole("button", { name: "Confirmar reembolso" }).click();
    await expect(page.getByText("Reembolso parcial realizado.")).toBeVisible();
    expect(corpos[0]).toMatchObject({ amountCents: 500, reason: "Cliente desistiu" });
    expect(chaves[0]).toMatch(/[0-9a-f-]{20,}/);
  });

  test("reembolso total manda amountCents nulo e mostra o erro do servidor", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/charges/*/refunds", (route) => route.fulfill({ status: 409, json: { code: "REFUND_NOT_ALLOWED", message: "Esta venda já foi reembolsada." } }));
    await page.goto("/vendas");
    await page.getByRole("button", { name: /Ver detalhes da venda 1111111/ }).click();
    await page.getByRole("button", { name: "Mais ações da venda" }).click();
    await page.getByRole("menuitem", { name: "Reembolsar venda" }).click();
    await page.getByRole("button", { name: "Confirmar reembolso" }).click();
    await expect(page.getByText("Esta venda já foi reembolsada.")).toBeVisible();
  });

  test("exportar baixa o CSV do filtro atual", async ({ page }) => {
    await preparar(page);
    let pedida = "";
    await page.route("**/api/v1/sales/export?**", (route) => { pedida = route.request().url(); return route.fulfill({ status: 200, contentType: "text/csv; charset=utf-8", body: "﻿ID da venda;Data\r\n1111111;25/09/2026 11:42\r\n" }); });
    await page.goto("/vendas");
    const [download] = await Promise.all([page.waitForEvent("download"), page.getByRole("button", { name: "Exportar" }).click()]);
    expect(download.suggestedFilename()).toMatch(/^vendas-paysi-\d{4}-\d{2}-\d{2}\.csv$/);
    expect(pedida).toContain("tab=approved");
    await expect(page.getByText(/Exportação concluída/)).toBeVisible();
  });

  test("submenu Reembolsos no menu e a lista de reembolsos", async ({ page }) => {
    await preparar(page);
    await page.route("**/api/v1/refunds?**", (route) => route.fulfill({ json: { items: [{ id: "r1", chargeId: ID_PAGO, saleCode: "1111111", productName: "Cartilha do Aprovado PMES", buyerName: "Henrique Viana da Silva", buyerEmail: "henriquesjv@hotmail.com", amountCents: 990, reason: "Cliente desistiu", status: "SUCCEEDED", requestedBy: "SELLER", createdAt: "2026-09-25T15:00:00Z", settledAt: null }], page: 1, size: 10, total: 1, totalPages: 1 } }));
    await page.goto("/vendas");
    await page.getByRole("link", { name: "Reembolsos" }).click();
    await expect(page).toHaveURL(/\/vendas\/reembolsos$/);
    await expect(page.getByRole("heading", { name: "Reembolsos" })).toBeVisible();
    await expect(page.getByText("Concluído", { exact: true })).toBeVisible();
    await expect(page.getByText("R$ 9,90")).toBeVisible();
    await expect(page.getByText("Motivo: Cliente desistiu")).toBeVisible();
  });

  test("estado vazio e erro têm mensagens claras", async ({ page }) => {
    await preparar(page, () => pagina([], { summary: { count: 0, netCents: 0 } }));
    await page.goto("/vendas");
    await expect(page.getByText("Nenhuma venda encontrada")).toBeVisible();
    await expect(page.getByRole("region", { name: "Vendas encontradas" })).toContainText("0");
  });

  test("passa no axe com a lista e com o painel de detalhes abertos", async ({ page }) => {
    await preparar(page);
    await page.goto("/vendas");
    await expect(page.getByText("Henrique Viana da Silva").first()).toBeVisible();
    let axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), "lista").toEqual([]);
    await page.getByRole("button", { name: /Ver detalhes da venda 1111111/ }).click();
    await expect(page.getByRole("tab", { name: "Cliente" })).toBeVisible();
    axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), "detalhe").toEqual([]);
  });
});
