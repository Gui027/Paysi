import { test, expect } from "@playwright/test";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const AFILIACAO = (status: "PENDING" | "APPROVED") => ({
  id: "af_1", productId: "prod_1", productName: "Curso", sellerId: "s", sellerName: "Vendedor", affiliateId: "a", affiliateName: "Afiliado",
  commissionBps: status === "APPROVED" ? 2500 : 0, recurrence: "FIRST_CHARGE", status, endedReason: null, approvedAt: null, endedAt: null, createdAt: "2026-09-25T12:00:00Z",
});

test.describe("convite de afiliado", () => {
  test("aprovação automática mostra a comissão congelada", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/affiliations", (route) => route.fulfill({ status: 201, json: AFILIACAO("APPROVED") }));
    await page.goto("/afiliar/prod_1");
    await page.getByRole("button", { name: "Quero ser afiliado" }).click();
    await expect(page.getByText(/aprovada com comissão de 25%/i)).toBeVisible();
  });

  test("aprovação manual avisa que o pedido está em análise", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/affiliations", (route) => route.fulfill({ status: 201, json: AFILIACAO("PENDING") }));
    await page.goto("/afiliar/prod_1");
    await page.getByRole("button", { name: "Quero ser afiliado" }).click();
    await expect(page.getByText(/vai analisar/i)).toBeVisible();
  });

  test("sem KYC aprovado orienta a verificar a conta", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/affiliations", (route) => route.fulfill({ status: 403, json: { code: "AFFILIATE_KYC_REQUIRED", message: "Conclua a verificação da conta para pedir afiliação" } }));
    await page.goto("/afiliar/prod_1");
    await page.getByRole("button", { name: "Quero ser afiliado" }).click();
    await expect(page.getByRole("link", { name: "Verificar minha conta" })).toBeVisible();
  });
});
