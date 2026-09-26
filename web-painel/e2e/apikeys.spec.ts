import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const CONTA = "5b1c1c68-2c0e-4a51-9d0c-6d3c1d7f0a11";
const CHAVE = {
  id: "k1", name: "API ERP", keyHint: "*****4953", scopes: ["reports", "products", "sales", "sales_refund", "affiliates", "finance", "webhooks"],
  createdAt: "2026-07-29T15:46:00Z", lastUsedAt: null, clientId: "27b02a9e-f258-42e0-ab61-96a6e444a11a", accountId: CONTA,
};
const SEGREDO = "a".repeat(60) + "4953";

async function preparar(page: Page, itens: unknown[] = [CHAVE]) {
  const pedidos: { metodo: string; url: URL; corpo: Record<string, unknown> | null }[] = [];
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/api-keys**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    pedidos.push({ metodo: request.method(), url, corpo: request.postData() ? JSON.parse(request.postData()!) : null });
    if (request.method() === "GET") return route.fulfill({ json: itens });
    if (request.method() === "DELETE") return route.fulfill({ status: 204 });
    if (request.method() === "POST") return route.fulfill({ status: 201, json: { key: { ...CHAVE, id: "k2", name: "Nova", keyHint: "*****4953" }, clientSecret: SEGREDO } });
    return route.fulfill({ json: CHAVE });
  });
  return pedidos;
}

test.describe("apps e API", () => {
  test("Apps mostra Webhooks e API e o menu leva até lá", async ({ page }) => {
    await preparar(page);
    await page.goto("/inicio");
    await page.getByRole("link", { name: "Apps", exact: true }).click();
    await expect(page).toHaveURL(/\/apps$/);
    await expect(page.getByRole("heading", { name: "Apps" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Webhooks" })).toHaveAttribute("href", "/integracoes");
    await page.getByRole("link", { name: "API", exact: true }).click();
    await expect(page).toHaveURL(/\/apps\/api$/);
    await expect(page.getByRole("heading", { name: "API" })).toBeVisible();
  });

  test("lista as chaves com dica, datas e busca, e passa no axe", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/api");
    for (const coluna of ["Nome", "API Key", "Criada em", "Último uso"]) await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    await expect(page.getByRole("cell", { name: "API ERP", exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "*****4953" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "------" })).toBeVisible();
    await page.getByRole("searchbox").fill("erp");
    await expect.poll(() => pedidos.at(-1)?.url.searchParams.get("q")).toBe("erp");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("cria a chave: valida, respeita a dependência de reembolso e mostra o segredo uma vez", async ({ page, context }) => {
    await context.grantPermissions(["clipboard-read", "clipboard-write"]);
    const pedidos = await preparar(page);
    await page.goto("/apps/api");
    await page.getByRole("button", { name: "Criar API Key" }).click();
    await expect(page.getByRole("heading", { name: "Criar API Key" })).toBeVisible();
    for (const nome of ["Relatórios", "Produtos", "Vendas", "Reembolsar vendas", "Afiliados", "Financeiro", "Webhooks"]) await expect(page.getByLabel(nome, { exact: true })).toBeChecked();

    await page.getByRole("dialog").getByRole("button", { name: "Criar API Key" }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Informe um nome");

    await page.getByLabel("Vendas", { exact: true }).uncheck();
    await expect(page.getByLabel("Reembolsar vendas")).not.toBeChecked();
    await page.getByLabel("Reembolsar vendas").check();
    await expect(page.getByLabel("Vendas", { exact: true })).toBeChecked();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

    await page.getByRole("button", { name: "(Desmarcar todos)" }).click();
    await page.getByRole("textbox", { name: "Nome" }).fill("Nova");
    await page.getByRole("dialog").getByRole("button", { name: "Criar API Key" }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Escolha ao menos um endpoint");
    await page.getByLabel("Vendas", { exact: true }).check();
    await page.getByLabel("Financeiro").check();
    await page.getByRole("dialog").getByRole("button", { name: "Criar API Key" }).click();

    await expect(page.getByRole("dialog", { name: "Copiar sua API Key" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "client_secret" })).toHaveValue(SEGREDO);
    await expect(page.getByRole("textbox", { name: "client_id" })).toHaveValue(CHAVE.clientId);
    await expect(page.getByRole("textbox", { name: "account_id" })).toHaveValue(CONTA);
    expect(pedidos.filter(item => item.metodo === "POST").at(-1)?.corpo).toEqual({ name: "Nova", scopes: ["sales", "finance"] });
    await page.getByRole("button", { name: "Copiar client_secret" }).click();
    await expect(page.getByText("client_secret copiado.")).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(SEGREDO);
  });

  test("edita: mostra os dados de conexão sem revelar o segredo e salva as alterações", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/api");
    await page.getByRole("button", { name: "Ações da API Key API ERP" }).click();
    await page.getByRole("menuitem", { name: "Editar" }).click();
    await expect(page.getByRole("heading", { name: "Editar API Key" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "client_id" })).toHaveValue(CHAVE.clientId);
    await expect(page.getByRole("textbox", { name: "client_secret" })).toHaveValue("**************");
    await expect(page.getByRole("textbox", { name: "account_id" })).toHaveValue(CONTA);
    await page.getByLabel("Webhooks").uncheck();
    await page.getByRole("textbox", { name: "Nome" }).fill("API ERP 2");
    await page.getByRole("button", { name: "Salvar alterações" }).click();
    await expect(page.getByText("API Key atualizada")).toBeVisible();
    expect(pedidos.filter(item => item.metodo === "PUT").at(-1)?.corpo).toEqual({ name: "API ERP 2", scopes: ["reports", "products", "sales", "sales_refund", "affiliates", "finance"] });
  });

  test("exclui a chave depois de confirmar", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/api");
    await page.getByRole("button", { name: "Ações da API Key API ERP" }).click();
    await page.getByRole("menuitem", { name: "Excluir" }).click();
    await expect(page.getByRole("dialog", { name: "Excluir API Key" })).toBeVisible();
    await page.getByRole("dialog").getByRole("button", { name: "Excluir" }).click();
    await expect(page.getByText("API Key excluída")).toBeVisible();
    expect(pedidos.some(item => item.metodo === "DELETE" && item.url.pathname.endsWith("/k1"))).toBe(true);
  });

  test("a documentação da API abre na Central de Ajuda", async ({ page }) => {
    await page.goto("/ajuda/api");
    await expect(page.getByRole("heading", { name: "API da Paysi" })).toBeVisible();
    await expect(page.getByText("/v1/public/oauth/token").first()).toBeVisible();
    await expect(page.getByRole("cell", { name: "/v1/public/sales/{id}/refund" })).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });
});
