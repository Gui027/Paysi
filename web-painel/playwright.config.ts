import { defineConfig, devices } from "@playwright/test";

/**
 * E2E do painel (FE-15.1). Roda contra `next start` (build de produção), com
 * as chamadas para /api/** interceptadas (ver e2e/fixtures.ts) — este repo
 * não tem infraestrutura de backend+banco disponível em CI para subir um E2E
 * de ponta a ponta real, então a fronteira de contrato (lib/api.ts) é o ponto
 * de mock, e o teste continua validando o app de verdade: renderização,
 * navegação, formulários, teclado e acessibilidade (axe-core).
 *
 * `workers: 1`: neste ambiente sandboxed, lançar múltiplas instâncias do
 * Chromium em paralelo derruba o worker (crash nativo) — rodar em série é o
 * que se mostrou estável aqui. Ver mesma decisão em web-checkout/playwright.config.ts.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  timeout: 45_000,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["html", { open: "never" }]],
  use: {
    baseURL: "http://localhost:3000",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    command: "npm run start -- --port 3000",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
