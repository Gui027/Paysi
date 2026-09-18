import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const BANK_ACCOUNT = {
  id: "bank_1",
  bankCode: "260",
  branch: "0001",
  numberLast4: "1234",
  verifiedAt: "2026-09-01T00:00:00Z",
};

const BALANCE = {
  guarantee: 0,
  pending: 0,
  reserve: 0,
  available: 50000,
  debt: 0,
  asOf: "2026-09-17T12:00:00Z",
};

async function abrirSaqueComContaCadastrada(page: import("@playwright/test").Page) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/accounts/me/balance", async (route) => {
    await route.fulfill({ json: BALANCE });
  });
  await page.addInitScript((bank) => {
    window.sessionStorage.setItem("paysi:bank-account", JSON.stringify(bank));
  }, BANK_ACCOUNT);
  await page.goto("/saldo/sacar");
}

test.describe("saque (payout)", () => {
  test("fluxo feliz: valor, MFA e recibo do saque", async ({ page }) => {
    await abrirSaqueComContaCadastrada(page);

    await page.route("**/api/v1/mfa/challenges", async (route) => {
      await route.fulfill({ json: { challengeId: "mfa_1", operation: "PAYOUT", expiresAt: "2026-09-17T12:05:00Z", verified: false } });
    });
    await page.route("**/api/v1/mfa/challenges/mfa_1/verify", async (route) => {
      await route.fulfill({ json: { challengeId: "mfa_1", operation: "PAYOUT", expiresAt: "2026-09-17T12:05:00Z", verified: true } });
    });
    await page.route("**/api/v1/accounts/me/payouts", async (route) => {
      await route.fulfill({ json: { payoutId: "payout_1", status: "PROCESSING", receiptUrl: null, idempotentReplay: false } });
    });

    await expect(page.getByText(/saldo disponível/i)).toBeVisible();
    await page.getByLabel(/valor do saque/i).fill("100,00");
    await page.getByRole("button", { name: /continuar com mfa/i }).click();

    await expect(page.getByRole("dialog")).toBeVisible();
    await page.getByLabel(/código mfa/i).fill("123456");
    await page.getByRole("button", { name: /confirmar código/i }).click();

    await expect(page.getByText(/operação recebida/i)).toBeVisible();
    await expect(page.getByText("payout_1")).toBeVisible();
  });

  test("saque abaixo do mínimo é bloqueado no cliente antes de chamar a API", async ({ page }) => {
    await abrirSaqueComContaCadastrada(page);

    let mfaCalled = false;
    await page.route("**/api/v1/mfa/challenges", async (route) => {
      mfaCalled = true;
      await route.fulfill({ json: { challengeId: "mfa_x", operation: "PAYOUT", expiresAt: "2026-09-17T12:05:00Z", verified: false } });
    });

    await page.getByLabel(/valor do saque/i).fill("1,00");
    await page.getByRole("button", { name: /continuar com mfa/i }).click();

    await expect(page.getByText(/o saque mínimo é de/i)).toBeVisible();
    expect(mfaCalled).toBe(false);
  });

  test("sem conta bancária cadastrada mostra estado vazio com ação", async ({ page }) => {
    await mockSessao(page);
    await mockDashboardVazio(page);
    await page.route("**/api/v1/accounts/me/balance", async (route) => {
      await route.fulfill({ json: BALANCE });
    });
    await page.goto("/saldo/sacar");

    await expect(page.getByText(/cadastre uma conta bancária/i)).toBeVisible();
    await expect(page.getByRole("link", { name: /cadastrar conta/i })).toBeVisible();
  });

  test("não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await abrirSaqueComContaCadastrada(page);
    await expect(page.getByText(/saldo disponível/i)).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });
});
