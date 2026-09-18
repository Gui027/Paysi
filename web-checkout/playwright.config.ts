import { defineConfig, devices } from "@playwright/test";

/**
 * E2E do checkout (FE-15.1). Roda contra o build de produção via `vite preview`,
 * com todas as chamadas de API interceptadas (ver e2e/fixtures.ts) — este repo
 * não tem infraestrutura de backend+banco disponível em CI para um E2E de
 * ponta a ponta real, então a fronteira de contrato (src/lib/api.ts) é o ponto
 * de mock, e o teste continua validando o app de verdade: renderização,
 * validação de formulário, navegação por teclado e acessibilidade (axe-core).
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["html", { open: "never" }]],
  use: {
    baseURL: "http://localhost:5173",
    trace: "retain-on-failure",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    command: "npm run preview -- --port 5173",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
