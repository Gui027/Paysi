import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio, SESSAO_VENDEDOR } from "./fixtures";

const CHEIO = {
  period: { preset: "7d", from: "2026-09-20", to: "2026-09-26" },
  earnings: { state: "SUCCESS", data: { commissionCents: 14970, sales: 3, clicks: 40, conversionPercent: "7.5" } },
  balance: { state: "SUCCESS", data: { guarantee: 5000, pending: 2000, reserve: 0, available: 4990, debt: 0, asOf: "2026-09-26T12:00:00Z" } },
  nextReceivables: { state: "SUCCESS", data: [{ amountCents: 2990, availableAt: "2026-10-03T12:00:00Z" }] },
  affiliations: { state: "SUCCESS", data: { active: 2, pending: 1 } },
  alerts: { state: "SUCCESS", data: [{ id: "kyc", tone: "warning", title: "Verificação de identidade pendente", description: "Inicie a verificação para receber comissões.", actionUrl: "/saldo?aba=identidade" }] },
  topProducts: { state: "SUCCESS", data: [{ productId: "p1", productName: "Curso Pro", sales: 2, commissionCents: 9980 }, { productId: "p2", productName: "Ebook", sales: 1, commissionCents: 4990 }] },
  recentCommissions: { state: "SUCCESS", data: [
    { id: "c1", productName: "Curso Pro", commissionCents: 4990, status: "PAID", occurredAt: "2026-09-25T15:00:00Z" },
    { id: "c2", productName: "Ebook", commissionCents: 1500, status: "REFUNDED", occurredAt: "2026-09-24T15:00:00Z" },
  ] },
};
const VAZIO = {
  period: { preset: "today", from: "2026-09-26", to: "2026-09-26" },
  earnings: { state: "EMPTY" }, balance: { state: "EMPTY" }, nextReceivables: { state: "EMPTY" }, affiliations: { state: "EMPTY" },
  alerts: { state: "EMPTY" }, topProducts: { state: "EMPTY" }, recentCommissions: { state: "EMPTY" },
};

async function preparar(page: Page, resposta: (url: URL) => unknown = () => CHEIO) {
  const pedidos: URL[] = [];
  await mockSessao(page, { ...SESSAO_VENDEDOR, activeMode: "AFFILIATE" });
  await mockDashboardVazio(page);
  await page.route("**/api/v1/accounts/me/dashboard/affiliate**", (route) => {
    const url = new URL(route.request().url());
    pedidos.push(url);
    return route.fulfill({ json: resposta(url) });
  });
  return pedidos;
}

test.describe("dashboard do afiliado", () => {
  test("mostra comissões, cliques, conversão, saldo, produtos, afiliações e liberações", async ({ page }) => {
    await preparar(page);
    await page.goto("/inicio");
    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
    const comissoes = page.getByRole("region", { name: "Suas comissões" });
    await expect(comissoes).toContainText("R$");
    await expect(comissoes).toContainText("149,70");
    await expect(comissoes.getByText("Vendas indicadas")).toBeVisible();
    await expect(comissoes.getByText("Cliques nos seus links")).toBeVisible();
    await expect(comissoes).toContainText("40");
    await expect(comissoes).toContainText("7,5%");
    await expect(page.getByRole("region", { name: "Saldo" })).toContainText("49,90");
    await expect(page.getByRole("table", { name: "Produtos que mais rendem" })).toContainText("Curso Pro");
    await expect(page.getByRole("table", { name: "Produtos que mais rendem" })).toContainText("99,80");
    await expect(page.getByRole("region", { name: "Suas afiliações" })).toContainText("Aguardando aprovação");
    await expect(page.getByRole("table", { name: "Últimas comissões" })).toContainText("Reembolsada");
    await expect(page.getByRole("region", { name: "Próximas liberações" })).toContainText("29,90");
    await expect(page.getByRole("region", { name: "Alertas" })).toContainText("Verificação de identidade pendente");
    await expect(page.getByText("Assinaturas ativas")).toHaveCount(0);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("troca o período e pede o dashboard do afiliado, não o do produtor", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/inicio");
    await expect(page.getByRole("region", { name: "Suas comissões" })).toBeVisible();
    await page.getByRole("button", { name: "Últimos 30 dias" }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("period")).toBe("30d");
    await page.getByRole("button", { name: "Últimos 7 dias" }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("period")).toBe("7d");
  });

  test("conta nova mostra estados vazios com o caminho para a vitrine", async ({ page }) => {
    await preparar(page, () => VAZIO);
    await page.goto("/inicio");
    await expect(page.getByText("Nenhuma comissão em hoje")).toBeVisible();
    await expect(page.getByRole("region", { name: "Suas comissões" }).getByRole("link", { name: "vitrine" })).toHaveAttribute("href", "/vitrine");
    await expect(page.getByText("Você ainda não é afiliado de nenhum produto")).toBeVisible();
    await expect(page.getByText("Nenhuma comissão recente")).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("um bloco com erro não derruba os outros e sem cliques a conversão vira traço", async ({ page }) => {
    await preparar(page, () => ({ ...CHEIO, earnings: { state: "SUCCESS", data: { commissionCents: 0, sales: 1, clicks: 0, conversionPercent: null } }, topProducts: { state: "ERROR", code: "X", message: "Este bloco está temporariamente indisponível." } }));
    await page.goto("/inicio");
    await expect(page.getByRole("region", { name: "Suas comissões" })).toContainText("—");
    await expect(page.getByRole("region", { name: "Produtos que mais rendem" })).toContainText("temporariamente indisponível");
    await expect(page.getByRole("table", { name: "Últimas comissões" })).toBeVisible();
  });
});
