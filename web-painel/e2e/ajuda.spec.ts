import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

test.describe("central de ajuda", () => {
  test("o menu lateral abre a Ajuda em uma nova guia", async ({ page, context }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.goto("/inicio");
    const link = page.getByRole("link", { name: /^Ajuda/ });
    await expect(link).toHaveAttribute("target", "_blank");
    await expect(link).toHaveAttribute("href", "/ajuda");
    const nova = context.waitForEvent("page");
    await link.click();
    const guia = await nova;
    await guia.waitForLoadState();
    await expect(guia).toHaveURL(/\/ajuda$/);
    await expect(guia.getByRole("heading", { name: "Como podemos ajudar?" })).toBeVisible();
  });

  test("mostra as nove categorias, filtra pela busca e passa no axe", async ({ page }) => {
    await page.goto("/ajuda");
    await expect(page.getByRole("heading", { name: "Buscar em todas as categorias" })).toBeVisible();
    await expect(page.getByRole("list").getByRole("link")).toHaveCount(9);
    for (const nome of ["Comprei um produto", "Produtos", "Financeiro", "Sobre a Paysi", "Área de membros", "Perguntas frequentes", "Integrações", "Configurações", "Afiliados"]) {
      await expect(page.getByRole("link", { name: new RegExp(`^${nome}`) })).toBeVisible();
    }
    await expect(page.getByText("Não encontrou o que buscava?")).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.getByRole("searchbox").fill("integracoes");
    await expect(page.getByRole("list").getByRole("link")).toHaveCount(1);
    await page.getByRole("searchbox").fill("zzz");
    await expect(page.getByRole("status")).toContainText("Nenhuma categoria encontrada");
  });

  test("cada categoria abre uma página casca e o botão volta ao painel", async ({ page }) => {
    await page.goto("/ajuda");
    await page.getByRole("link", { name: /^Financeiro/ }).click();
    await expect(page).toHaveURL(/\/ajuda\/financeiro$/);
    await expect(page.getByRole("heading", { name: "Financeiro" })).toBeVisible();
    await expect(page.getByText("serão publicados em breve")).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await expect(page.getByRole("link", { name: "Ir para o site" })).toHaveAttribute("href", "/inicio");
    expect((await page.goto("/ajuda/nao-existe"))?.status()).toBe(404);
  });
});
