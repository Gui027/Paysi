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

  test("duplica a oferta, edita a cópia e lista os dois checkouts", async ({ page }) => {
    const COPIA = { ...OFERTA, id: "off_ed_2", slug: "curso-vendas-2", name: "Plano Pro (cópia)", createdAt: "2026-09-26T12:00:00Z" };
    let enviado: Record<string, unknown> | null = null;
    await page.route(`**/api/v1/offers/${OFERTA.id}/duplicate`, (route) => route.fulfill({ status: 201, json: COPIA }));
    await page.route(`**/api/v1/offers/${COPIA.id}`, async (route) => { enviado = route.request().postDataJSON(); await route.fulfill({ json: { ...COPIA, ...enviado } }); });
    await page.goto(`/produtos/${PRODUTO.id}?aba=checkout`);
    await page.getByRole("button", { name: "Duplicar Oferta 1" }).click();
    await expect(page.getByText("Oferta duplicada.")).toBeVisible();
    await expect(page.getByLabel("Nome da oferta")).toHaveValue("Plano Pro (cópia)");
    await page.getByLabel("Nome da oferta").fill("Plano Pro");
    await page.getByLabel("Preço em reais").fill("199,00");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Produto salvo.")).toBeVisible();
    expect(enviado).toMatchObject({ name: "Plano Pro", priceCents: 19900 });
    await page.getByRole("tab", { name: "Checkout" }).click();
    await expect(page.getByRole("row")).toHaveCount(3);
    await expect(page.getByRole("button", { name: "Publicar Plano Pro" })).toBeVisible();
  });

  test("salva a URL de retorno e recusa endereço sem https", async ({ page }) => {
    let oferta: Record<string, unknown> | null = null;
    await page.route(`**/api/v1/offers/${OFERTA.id}`, async (route) => { oferta = route.request().postDataJSON(); await route.fulfill({ json: { ...OFERTA, ...oferta } }); });
    await page.goto(`/produtos/${PRODUTO.id}?aba=configuracoes`);
    await page.getByLabel("URL de retorno").fill("http://seusite.com/obrigado");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText(/começando com https/i)).toBeVisible();
    expect(oferta).toBeNull();
    await page.getByLabel("URL de retorno").fill("https://seusite.com/obrigado");
    await page.getByRole("button", { name: "Salvar produto" }).first().click();
    await expect(page.getByText("Produto salvo.")).toBeVisible();
    expect(oferta).toMatchObject({ returnUrl: "https://seusite.com/obrigado" });
  });

  test("mostra o exemplo de link com ref na aba Checkout", async ({ page }) => {
    await page.goto(`/produtos/${PRODUTO.id}?aba=checkout`);
    await expect(page.getByLabel("Exemplo de link com identificação do cliente")).toHaveValue(/checkout\/curso-vendas\?ref=ID_DO_CLIENTE/);
  });

  test("publica o checkout e mostra o link", async ({ page }) => {
    await page.route(`**/api/v1/offers/${OFERTA.id}/publish`, (route) => route.fulfill({ json: { published: true, requiredAction: null, actionUrl: null, offer: { ...OFERTA, status: "PUBLISHED" } } }));
    await page.goto(`/produtos/${PRODUTO.id}?aba=checkout`);
    await page.getByRole("button", { name: "Publicar Oferta 1" }).click();
    await expect(page.getByText("Checkout publicado.")).toBeVisible();
    await page.getByRole("tab", { name: "Links" }).click();
    await expect(page.getByLabel("URL do checkout de Oferta 1")).toHaveValue(/checkout\/curso-vendas/);
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
