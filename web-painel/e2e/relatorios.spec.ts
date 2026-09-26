import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const CHART = {
  points: [
    { label: "09/10", valueCents: 2146, heightPercent: "85.8", secondCents: 0, secondPercent: "0.0" },
    { label: "26/10", valueCents: 89, heightPercent: "3.6", secondCents: 0, secondPercent: "0.0" },
  ],
  ticks: [0, 500, 1000, 1500, 2000, 2500].map((cents, index) => ({ cents, bottomPercent: `${index * 20}.0` })),
};
const VAZIO = { points: [], ticks: [0, 1, 2, 3, 4, 5].map((cents, index) => ({ cents, bottomPercent: `${index * 20}.0` })) };

function relatorio(id: string, colunas: [string, string, string][], linhas: (string | number)[][], extra: Record<string, unknown> = {}) {
  return {
    id, title: id, columns: colunas.map(([key, label, type]) => ({ key, label, type })), rows: linhas, chart: VAZIO, totals: {},
    page: 1, size: 10, total: linhas.length, totalPages: 1, ...extra,
  };
}

const PRODUTO = relatorio("produto", [["label", "Produto", "text"], ["sales", "Vendas", "int"], ["total", "Total líquido", "money"]],
  [["Rangu", 3, 12534], ["Curso Pro", 1, 4342]], { chart: CHART, totals: { total: 16876 } });
const SALDO = relatorio("saldo-receber", [["date", "Data", "date"], ["total", "Total", "money"]],
  [["2026-10-09", 2146], ["2026-10-26", 89]], { chart: CHART, totals: { total: 2235 } });
const CARTAO = relatorio("recebiveis-cartao",
  [["date", "Data", "date"], ["arrangement", "Arranjo", "text"], ["receivable", "A receber", "money"], ["contract", "Efeito de contrato", "money"]],
  [["2026-10-09", "Cartão de crédito", 2146, 0]], { chart: CHART, totals: { receivable: 2146, contract: 0 } });
const CANCELADAS = relatorio("assinaturas-canceladas",
  [["date", "Data", "datetime"], ["product", "Produto", "text"], ["plan", "Plano", "text"], ["client", "Cliente", "text"], ["email", "E-mail", "text"], ["reason", "Motivo do cancelamento", "text"]],
  [["2026-09-05T21:01:00Z", "Garimpa", "Plano Mensal", "OLACLICK", "eduardo@olaclick.com", "Não informado"]]);
const ABANDONADAS = relatorio("abandonadas",
  [["date", "Data", "datetime"], ["product", "Produto", "text"], ["client", "Cliente", "text"], ["email", "E-mail", "text"], ["phone", "Telefone", "text"], ["value", "Valor", "money"], ["status", "Situação", "text"]],
  [["2026-09-25T10:00:00Z", "Rangu", "Ana Maria", "ana@exemplo.com", "11999998888", 4990, "Aguardando pagamento"],
   ["2026-09-24T10:00:00Z", "Rangu", "Bia", "bia@exemplo.com", "", 4990, "Expirado"]]);

async function preparar(page: Page, respostas: Record<string, (url: URL) => unknown>) {
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [{ id: "p1", name: "Rangu" }], nextCursor: null } }));
  await page.route("**/api/v1/reports/**", (route) => {
    const url = new URL(route.request().url());
    const id = url.pathname.split("/").pop()!;
    if (url.pathname.endsWith("/export")) {
      const formato = url.searchParams.get("format");
      return route.fulfill({ status: 200, contentType: formato === "xlsx" ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv", body: "dados" });
    }
    return route.fulfill({ json: (respostas[id] ?? (() => relatorio(id, [], [])))(url) });
  });
}

test.describe("relatórios", () => {
  test("o menu leva ao painel de relatórios com os nove relatórios", async ({ page }) => {
    await preparar(page, {});
    await page.goto("/relatorios");
    await expect(page.getByRole("heading", { name: "Relatórios" })).toBeVisible();
    for (const nome of ["Receita de co-produção", "Receita por produto", "Vendas abandonadas", "Engajamento dos alunos", "Receita por afiliado", "Saldo a receber", "Recebíveis de cartão", "Assinaturas canceladas", "Agente recuperador de vendas"]) {
      await expect(page.getByRole("link", { name: nome })).toBeVisible();
    }
    await expect(page.getByRole("link", { name: "Relatórios", exact: true }).first()).toBeVisible();
    await page.getByRole("link", { name: "Receita por produto" }).click();
    await expect(page).toHaveURL(/\/relatorios\/produto$/);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("receita por produto mostra gráfico, tabela e filtra pelo período", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { produto: (url) => { pedidos.push(url); return PRODUTO; } });
    await page.goto("/relatorios/produto");
    await expect(page.getByRole("heading", { name: "Receita por produto" })).toBeVisible();
    await expect(page.getByRole("figure", { name: "Gráfico: Receita por produto" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Rangu" })).toBeVisible();
    await expect(page.getByRole("cell", { name: /125,34/ })).toBeVisible();
    await expect(page.getByRole("button", { name: /Tempo todo/ })).toBeVisible();
    await page.getByRole("button", { name: /Tempo todo/ }).click();
    await page.getByRole("button", { name: "Últimos 7 dias" }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("from")).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(pedidos.at(-1)?.searchParams.get("to")).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("o calendário escolhe um intervalo de datas", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { produto: (url) => { pedidos.push(url); return PRODUTO; } });
    await page.goto("/relatorios/produto");
    await page.getByRole("button", { name: /Tempo todo/ }).click();
    await page.getByRole("button", { name: /^01\/\d{2}\/\d{4}$/ }).click();
    await page.getByRole("button", { name: /^10\/\d{2}\/\d{4}$/ }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("from")).toMatch(/-01$/);
    expect(pedidos.at(-1)?.searchParams.get("to")).toMatch(/-10$/);
    await expect(page.getByRole("button", { name: /01\/\d{2}\/\d{4} – 10\/\d{2}\/\d{4}/ })).toBeVisible();
  });

  test("saldo a receber troca de aba, usa próximos 30 dias e exporta em xlsx e csv", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { "saldo-receber": (url) => { pedidos.push(url); return SALDO; } });
    await page.goto("/relatorios/saldo-receber");
    await expect(page.getByRole("tab", { name: "Cartão de crédito" })).toHaveAttribute("aria-selected", "true");
    await expect(page.getByRole("button", { name: /Próximos 30 dias/ })).toBeVisible();
    await expect(page.getByRole("cell", { name: /21,46/ })).toBeVisible();
    await expect(page.getByRole("cell", { name: "09/10/2026" })).toBeVisible();
    await page.getByRole("tab", { name: "Pix e Boleto" }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("tab")).toBe("offline");
    await page.getByRole("tab", { name: "Internacional" }).click();
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("tab")).toBe("international");

    await page.getByRole("button", { name: "Exportar" }).click();
    const xlsx = page.waitForRequest((request) => request.url().includes("/export") && request.url().includes("format=xlsx"));
    const download = page.waitForEvent("download");
    await page.getByRole("button", { name: /Planilha do Excel/ }).click();
    await xlsx;
    expect((await download).suggestedFilename()).toBe("paysi_saldo_receber.xlsx");
    await expect(page.getByText("Exportação iniciada com sucesso.")).toBeVisible();

    await page.getByRole("button", { name: "Exportar" }).click();
    const csv = page.waitForRequest((request) => request.url().includes("format=csv"));
    const downloadCsv = page.waitForEvent("download");
    await page.getByRole("button", { name: /Arquivo CSV/ }).click();
    await csv;
    expect((await downloadCsv).suggestedFilename()).toBe("paysi_saldo_receber.csv");
  });

  test("recebíveis de cartão mostra os cartões, a legenda e a tabela", async ({ page }) => {
    await preparar(page, { "recebiveis-cartao": () => CARTAO });
    await page.goto("/relatorios/recebiveis-cartao");
    await expect(page.getByRole("region", { name: "A receber" })).toContainText("21,46");
    await expect(page.getByRole("region", { name: "Efeito de contrato" })).toContainText("0,00");
    await expect(page.getByText("Cartão de crédito")).toBeVisible();
    for (const coluna of ["Data", "Arranjo", "A receber", "Efeito de contrato"]) await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("assinaturas canceladas busca por texto e filtra por produto", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { "assinaturas-canceladas": (url) => { pedidos.push(url); return CANCELADAS; } });
    await page.goto("/relatorios/assinaturas-canceladas");
    await expect(page.getByRole("cell", { name: "OLACLICK", exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Plano Mensal" })).toBeVisible();
    await page.getByRole("searchbox").fill("olaclick");
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("q")).toBe("olaclick");
    await page.getByRole("combobox").selectOption("p1");
    await expect.poll(() => pedidos.at(-1)?.searchParams.get("productId")).toBe("p1");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("agente recuperador abre o WhatsApp com a mensagem pronta", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { abandonadas: (url) => { pedidos.push(url); return ABANDONADAS; } });
    await page.goto("/relatorios/agente-recuperador");
    await expect(page.getByRole("heading", { name: "Agente recuperador de vendas" })).toBeVisible();
    await expect(page.getByRole("button", { name: /Últimos 7 dias/ })).toBeVisible();
    const link = page.getByRole("link", { name: "Chamar no WhatsApp" });
    await expect(link).toHaveCount(1);
    await expect(link).toHaveAttribute("href", /^https:\/\/wa\.me\/5511999998888\?text=/);
    await expect(page.getByText("Sem telefone")).toBeVisible();
    expect(pedidos[0].searchParams.get("from")).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  test("co-produção e engajamento abrem com aviso enquanto não existem", async ({ page }) => {
    const pedidos: URL[] = [];
    await preparar(page, { "co-producao-enviada": (url) => { pedidos.push(url); return relatorio("co-producao-enviada", [["producer", "Produtor", "text"]], []); } });
    await page.goto("/relatorios/co-producao-recebida");
    await expect(page.getByRole("tab", { name: "Receita recebida" })).toHaveAttribute("aria-selected", "true");
    await expect(page.getByText(/co-produção ainda não está disponível/)).toBeVisible();
    await page.getByRole("tab", { name: "Receita enviada" }).click();
    await expect.poll(() => pedidos.length).toBeGreaterThan(0);
    await page.goto("/relatorios/alunos");
    await expect(page.getByText(/área de membros estiver disponível/)).toBeVisible();
  });
});
