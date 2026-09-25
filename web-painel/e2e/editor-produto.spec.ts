import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const PRODUTO = { id: "prod_ed_1", name: "Curso de Vendas", description: "Aprenda a vender.", segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: false, status: "DRAFT", createdAt: "2026-09-25T12:00:00Z" };
const OFERTA = { id: "off_ed_1", productId: PRODUTO.id, chargeType: "ONE_TIME", segment: "DIGITAL", slug: "curso-vendas", priceCents: 9700, cycle: null, trialDays: 0, trialRequiresCard: true, guaranteeDays: 7, maxInstallments: 1, boletoDueDays: 3, boletoAdvanceDays: 5, paymentMethods: ["PIX", "CARD"], payoutDelay: "D32", status: "DRAFT", availableAt: "2026-09-25T12:00:00Z", immutableFields: [], createdAt: "2026-09-25T12:00:00Z", updatedAt: "2026-09-25T12:00:00Z" };
const PROGRAMA_PADRAO = { productId: PRODUTO.id, commissionBps: 3000, recurrence: "FIRST_CHARGE", autoApprove: false, supportEmail: null, description: null };

test.describe("editor do produto (abas)", () => {
  let programaEnviado: Record<string, unknown> | null = null;

  test.beforeEach(async ({ page }) => {
    programaEnviado = null;
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route(`**/api/v1/products/${PRODUTO.id}`, async (route) => {
      if (route.request().method() === "PUT") await route.fulfill({ json: { ...PRODUTO, ...route.request().postDataJSON() } });
      else await route.fulfill({ json: PRODUTO });
    });
    await page.route(`**/api/v1/products/${PRODUTO.id}/offers`, (route) => route.fulfill({ json: [OFERTA] }));
    await page.route(`**/api/v1/products/${PRODUTO.id}/affiliate-program`, async (route) => {
      if (route.request().method() === "PUT") {
        programaEnviado = route.request().postDataJSON();
        await route.fulfill({ json: { productId: PRODUTO.id, ...programaEnviado } });
      } else await route.fulfill({ json: PROGRAMA_PADRAO });
    });
  });

  test("salva nome, preço e parcelamento em uma só ação", async ({ page }) => {
    let oferta: Record<string, unknown> | null = null;
    await page.route(`**/api/v1/offers/${OFERTA.id}`, async (route) => { oferta = route.request().postDataJSON(); await route.fulfill({ json: { ...OFERTA, ...oferta } }); });
    await page.goto(`/produtos/${PRODUTO.id}`);
    await expect(page.getByLabel("Preço em reais")).toHaveValue("97,00");
    await page.getByLabel("Preço em reais").fill("147,50");
    await page.getByRole("tab", { name: "Configurações" }).click();
    await page.getByLabel("Parcelamento").selectOption("6");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Produto salvo.")).toBeVisible();
    expect(oferta).toMatchObject({ priceCents: 14750, maxInstallments: 6 });
  });

  test("configura o programa de afiliados e salva junto com o produto", async ({ page }) => {
    await page.route(`**/api/v1/offers/${OFERTA.id}`, (route) => route.fulfill({ json: OFERTA }));
    await page.goto(`/produtos/${PRODUTO.id}?aba=afiliados`);
    await page.getByRole("switch", { name: "Habilitar programa de afiliados" }).check();
    await page.getByRole("switch", { name: /aprovar cada solicitação/i }).uncheck();
    await page.getByLabel("Comissão em porcentagem").fill("25");
    await page.getByLabel("E-mail de suporte para afiliados").fill("ajuda@loja.com");
    await expect(page.getByLabel("Link de convite de afiliado")).toHaveValue(new RegExp(`/afiliar/${PRODUTO.id}$`));
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Produto salvo.")).toBeVisible();
    expect(programaEnviado).toMatchObject({ commissionBps: 2500, autoApprove: true, supportEmail: "ajuda@loja.com" });
  });

  test("rejeita comissão acima de 50%", async ({ page }) => {
    await page.goto(`/produtos/${PRODUTO.id}?aba=afiliados`);
    await page.getByRole("switch", { name: "Habilitar programa de afiliados" }).check();
    await page.getByLabel("Comissão em porcentagem").fill("60");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Informe uma comissão entre 0 e 50%.")).toBeVisible();
    expect(programaEnviado).toBeNull();
  });

  test("publica o checkout e mostra o link", async ({ page }) => {
    await page.route(`**/api/v1/offers/${OFERTA.id}/publish`, (route) => route.fulfill({ json: { published: true, requiredAction: null, actionUrl: null, offer: { ...OFERTA, status: "PUBLISHED" } } }));
    await page.goto(`/produtos/${PRODUTO.id}?aba=checkout`);
    await page.getByRole("button", { name: "Publicar", exact: true }).click();
    await expect(page.getByText("Checkout publicado.")).toBeVisible();
    await page.getByRole("tab", { name: "Links" }).click();
    await expect(page.getByLabel("URL do checkout")).toHaveValue(/checkout\/curso-vendas/);
  });

  test("passa no axe em todas as abas (com o programa de afiliados aberto)", async ({ page }) => {
    for (const aba of ["", "?aba=configuracoes", "?aba=checkout", "?aba=afiliados", "?aba=links"]) {
      await page.goto(`/produtos/${PRODUTO.id}${aba}`);
      await expect(page.getByRole("tab", { name: "Geral" })).toBeVisible();
      if (aba === "?aba=afiliados") await page.getByRole("switch", { name: "Habilitar programa de afiliados" }).check();
      const axe = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
      expect(axe.violations.filter(v => v.impact === "critical" || v.impact === "serious"), aba).toEqual([]);
    }
  });
});
