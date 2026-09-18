import { test, expect } from "@playwright/test";
import { OFERTA_SLUG, contractFixture } from "./fixtures";

/**
 * Aproximação de "checkout p95 < 1,5s" e "medir 4G" (FE-15.1).
 *
 * Este NÃO é Lighthouse CI nem throttling real de rede — este ambiente de CI
 * não tem esse toolchain disponível (ver PR: tentamos instalar Playwright,
 * que funcionou; Lighthouse CI teria custo de setup adicional não coberto
 * neste ciclo). Em vez disso, simulamos uma latência de rede compatível com
 * 4G médio (RTT ~150ms, ver referência de latência 4G da Web Almanac/CrUX)
 * em cada resposta mockada e medimos o tempo até o formulário de checkout
 * ficar interativo. É uma aproximação best-effort no ambiente disponível,
 * não uma medição de Core Web Vitals de laboratório — o gap real está
 * documentado no corpo do PR.
 */
const LATENCIA_4G_MS = 150;

test("formulário de checkout fica interativo em menos de 1,5s sob latência 4G simulada", async ({ page }) => {
  await page.route(`**/v1/offers/${OFERTA_SLUG}/checkout`, async (route) => {
    await new Promise(resolve => setTimeout(resolve, LATENCIA_4G_MS));
    await route.fulfill({ json: contractFixture() });
  });

  const inicio = Date.now();
  await page.goto(`/${OFERTA_SLUG}`);
  await page.getByRole("button", { name: /pagar agora/i }).waitFor({ state: "visible" });
  const duracaoMs = Date.now() - inicio;

  console.log(`Checkout interativo em ${duracaoMs}ms (orçamento: 1500ms, latência 4G simulada: ${LATENCIA_4G_MS}ms)`);
  expect(duracaoMs).toBeLessThan(1500);
});
