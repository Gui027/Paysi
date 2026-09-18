import type { Page } from "@playwright/test";

/**
 * Fixtures/mocks da API do painel. Interceptamos no limite de lib/api.ts
 * (fetch para /api/**) — o front-end sob teste é o real, só a rede é
 * dublada, já que este repo não tem um backend+banco disponível para subir
 * em CI para um E2E ponta a ponta completo.
 */

export const SESSAO_VENDEDOR = {
  accountId: "acc_e2e_1",
  activeMode: "SELLER" as const,
  expiresAt: "2026-09-18T00:00:00Z",
};

export async function mockSessao(page: Page, sessao: Record<string, unknown> = SESSAO_VENDEDOR) {
  await page.route("**/api/v1/sessions/current", async (route) => {
    await route.fulfill({ json: sessao });
  });
}

export async function mockSessaoDeslogada(page: Page) {
  await page.route("**/api/v1/sessions/current", async (route) => {
    await route.fulfill({ status: 401, json: { code: "UNAUTHENTICATED", message: "Sessão expirada." } });
  });
}

const DASHBOARD_VAZIO = {
  period: { preset: "today", from: "2026-09-17", to: "2026-09-17" },
  salesToday: { state: "EMPTY" },
  balance: { state: "EMPTY" },
  nextReceivables: { state: "EMPTY" },
  subscriptions: { state: "EMPTY" },
  alerts: { state: "EMPTY" },
  recentSales: { state: "EMPTY" },
};

export async function mockDashboardVazio(page: Page) {
  await page.route("**/api/v1/accounts/me/dashboard**", async (route) => {
    await route.fulfill({ json: DASHBOARD_VAZIO });
  });
}
