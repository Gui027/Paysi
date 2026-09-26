import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const RANGU = { id: "p1", name: "Rangu", status: "ACTIVE", description: null, segment: "DIGITAL", chargeType: "ONE_TIME", affiliationEnabled: false, createdAt: "2026-01-01T00:00:00Z" };
const RASCUNHO = { ...RANGU, id: "p2", name: "Rascunho X", status: "DRAFT" };
const HOOK1 = { id: "w1", name: "Rangu Produção", url: "https://api.rangu.app/api/webhooks/paysi", productId: "p1", productName: "Rangu", events: ["PAYMENT.APPROVED", "PAYMENT.REFUNDED", "PAYMENT.PARTIALLY_REFUNDED"], enabled: true, createdAt: "2026-09-01T10:00:00Z" };
const HOOK2 = { id: "w2", name: "Utmify", url: "https://api.utmify.com.br/webhooks/paysi?id=6a1", productId: null, productName: null, events: ["PIX.GENERATED"], enabled: true, createdAt: "2026-08-01T10:00:00Z" };

const LOG1 = { eventId: "e1", eventType: "CART.ABANDONED", saleCode: "ABC1234", sentAt: "2026-09-20T21:25:56Z", status: "FAILED", statusCode: 500, attempts: 3 };
const LOG2 = { eventId: "e2", eventType: "PAYMENT.APPROVED", saleCode: "DEF5678", sentAt: "2026-09-21T10:00:00Z", status: "SUCCESS", statusCode: 200, attempts: 1 };
const DETALHE = {
  eventId: "e1", eventType: "CART.ABANDONED", url: "https://api.rangu.app/api/webhooks/paysi", sentAt: "2026-09-20T21:25:56Z", status: "FAILED", statusCode: 500, error: "HTTP_500",
  requestBody: '{"eventId":"e1","type":"CART.ABANDONED","data":{"buyer":{"email":"a@b.com"}}}', responseBody: "<html>erro</html>", attempts: 3, canResend: true,
};

type Pedido = { metodo: string; url: URL; corpo: Record<string, unknown> | null };

async function preparar(page: Page, opcoes: { hooks?: unknown[]; logs?: unknown[]; teste?: unknown } = {}) {
  const pedidos: Pedido[] = [];
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/products?**", (route) => route.fulfill({ json: { items: [RANGU, RASCUNHO], nextCursor: null } }));
  await page.route("**/api/v1/webhooks**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    pedidos.push({ metodo: request.method(), url, corpo: request.postData() ? JSON.parse(request.postData()!) : null });
    const caminho = url.pathname.replace("/api", "");
    const metodo = request.method();
    if (caminho === "/v1/webhooks" && metodo === "GET") return route.fulfill({ json: opcoes.hooks ?? [HOOK1, HOOK2] });
    if (caminho === "/v1/webhooks" && metodo === "POST") return route.fulfill({ status: 201, json: { endpoint: { ...HOOK1, id: "w3" }, secret: "segredo-criado-123" } });
    if (caminho === "/v1/webhooks/test") return route.fulfill({ json: opcoes.teste ?? { success: true, statusCode: 200, error: null, responseBody: "ok" } });
    if (caminho.endsWith("/rotate-secret")) return route.fulfill({ json: { endpointId: "w1", secret: "segredo-novo-456", previousSecretValidUntil: "2026-09-27T00:00:00Z" } });
    if (caminho.endsWith("/logs/resend")) return route.fulfill({ json: { sent: (JSON.parse(request.postData()!).eventIds as string[]).length } });
    if (caminho.endsWith("/resend")) return route.fulfill({ status: 202 });
    if (/\/logs\/[^/]+$/.test(caminho)) return route.fulfill({ json: DETALHE });
    if (caminho.endsWith("/logs")) return route.fulfill({ json: { items: opcoes.logs ?? [LOG1, LOG2], page: 1, size: 10, total: 2, totalPages: 1 } });
    if (metodo === "DELETE") return route.fulfill({ status: 204 });
    if (metodo === "PUT") return route.fulfill({ json: HOOK1 });
    return route.fulfill({ json: HOOK1 });
  });
  return pedidos;
}

test.describe("webhooks", () => {
  test("lista com produto, nome e URL, busca, filtro por produto e passa no axe", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks");
    await expect(page.getByRole("heading", { name: "Webhooks" })).toBeVisible();
    for (const coluna of ["Produto", "Nome", "URL"]) await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Todos que sou produtor" })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Rangu", exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Rangu Produção", exact: true })).toBeVisible();
    await page.getByRole("searchbox").fill("rangu");
    await expect.poll(() => pedidos.at(-1)?.url.searchParams.get("q")).toBe("rangu");
    await page.getByRole("combobox").selectOption("p1");
    await expect.poll(() => pedidos.at(-1)?.url.searchParams.get("productId")).toBe("p1");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("Apps → Webhooks abre a lista e o menu antigo Integrações redireciona", async ({ page }) => {
    await preparar(page);
    await page.goto("/apps");
    await page.getByRole("link", { name: "Webhooks" }).click();
    await expect(page).toHaveURL(/\/apps\/webhooks$/);
    await page.goto("/integracoes");
    await expect(page).toHaveURL(/\/apps\/webhooks$/);
  });

  test("cria o webhook: valida, testa a URL, escolhe produto e eventos e mostra o segredo uma vez", async ({ page, context }) => {
    await context.grantPermissions(["clipboard-read", "clipboard-write"]);
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks");
    await page.getByRole("button", { name: "Criar webhook" }).click();
    await expect(page.getByRole("heading", { name: "Criar webhook" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Testar Webhook" })).toBeDisabled();
    await expect(page.getByRole("textbox", { name: "Token" })).toHaveValue("Será gerado ao criar");

    await page.getByRole("dialog").getByRole("button", { name: "Criar", exact: true }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Informe o nome");
    await page.getByRole("textbox", { name: "Nome" }).fill("ERP");
    await page.getByRole("textbox", { name: "URL do Webhook" }).fill("http://inseguro.com");
    await page.getByRole("dialog").getByRole("button", { name: "Criar", exact: true }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("HTTPS");
    await page.getByRole("textbox", { name: "URL do Webhook" }).fill("https://erp.exemplo.com/hooks/paysi");
    await page.getByRole("dialog").getByRole("button", { name: "Criar", exact: true }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Selecione ao menos um evento");

    await page.getByRole("button", { name: "Testar Webhook" }).click();
    await expect(page.getByText("A URL está funcionando")).toBeVisible();
    expect(pedidos.find(item => item.url.pathname.endsWith("/test"))?.corpo).toEqual({ url: "https://erp.exemplo.com/hooks/paysi", endpointId: null });

    await expect(page.getByRole("option", { name: "Rangu" })).toHaveCount(2);
    await page.getByRole("dialog").getByLabel("Produtos").selectOption("p1");
    for (const evento of ["Boleto gerado", "Pix gerado", "Carrinho abandonado", "Compra recusada", "Compra aprovada", "Reembolso", "Chargeback", "Assinatura cancelada", "Assinatura atrasada", "Assinatura renovada"]) {
      await expect(page.getByLabel(evento, { exact: true })).not.toBeChecked();
    }
    await page.getByRole("button", { name: "(Selecionar todos)" }).click();
    await expect(page.getByLabel("Reembolso", { exact: true })).toBeChecked();
    await page.getByRole("button", { name: "(Desmarcar todos)" }).click();
    await page.getByLabel("Compra aprovada").check();
    await page.getByLabel("Reembolso", { exact: true }).check();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await page.getByRole("dialog").getByRole("button", { name: "Criar", exact: true }).click();

    await expect(page.getByRole("dialog", { name: "Copiar o segredo do webhook" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Segredo do webhook" })).toHaveValue("segredo-criado-123");
    expect(pedidos.filter(item => item.metodo === "POST" && item.url.pathname.endsWith("/webhooks")).at(-1)?.corpo).toEqual({
      name: "ERP", productId: "p1", url: "https://erp.exemplo.com/hooks/paysi", events: ["PAYMENT.APPROVED", "PAYMENT.REFUNDED", "PAYMENT.PARTIALLY_REFUNDED"],
    });
    await page.getByRole("button", { name: "Copiar segredo" }).click();
    await expect(page.getByText("Segredo copiado.")).toBeVisible();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe("segredo-criado-123");
  });

  test("teste que falha mostra o motivo", async ({ page }) => {
    await preparar(page, { teste: { success: false, statusCode: 500, error: "HTTP_500", responseBody: null } });
    await page.goto("/apps/webhooks");
    await page.getByRole("button", { name: "Criar webhook" }).click();
    await page.getByRole("textbox", { name: "URL do Webhook" }).fill("https://erp.exemplo.com/hooks");
    await page.getByRole("button", { name: "Testar Webhook" }).click();
    await expect(page.getByText(/Falhou \(HTTP 500\)/)).toBeVisible();
  });

  test("edita: mostra os eventos marcados, o token oculto, gera novo segredo e salva", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks");
    await page.getByRole("button", { name: "Ações do webhook Rangu Produção" }).click();
    await page.getByRole("menuitem", { name: "Editar" }).click();
    await expect(page.getByRole("heading", { name: "Editar webhook" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Nome" })).toHaveValue("Rangu Produção");
    await expect(page.getByRole("dialog").getByLabel("Produtos")).toHaveValue("p1");
    await expect(page.getByLabel("Compra aprovada")).toBeChecked();
    await expect(page.getByLabel("Reembolso", { exact: true })).toBeChecked();
    await expect(page.getByLabel("Pix gerado")).not.toBeChecked();
    await expect(page.getByRole("textbox", { name: "Token" })).toHaveValue("••••••••••••");

    await page.getByRole("button", { name: "Testar Webhook" }).click();
    expect(pedidos.find(item => item.url.pathname.endsWith("/test"))?.corpo).toEqual({ url: HOOK1.url, endpointId: "w1" });

    await page.getByLabel("Pix gerado").check();
    await page.getByRole("button", { name: "Salvar alterações" }).click();
    await expect(page.getByText("Webhook atualizado")).toBeVisible();
    expect(pedidos.filter(item => item.metodo === "PUT").at(-1)?.corpo).toEqual({
      name: "Rangu Produção", productId: "p1", url: HOOK1.url, events: ["PIX.GENERATED", "PAYMENT.APPROVED", "PAYMENT.REFUNDED", "PAYMENT.PARTIALLY_REFUNDED"],
    });

    await page.getByRole("button", { name: "Ações do webhook Rangu Produção" }).click();
    await page.getByRole("menuitem", { name: "Editar" }).click();
    await page.getByRole("button", { name: "Gerar novo segredo" }).click();
    await expect(page.getByRole("dialog", { name: "Novo segredo do webhook" })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Segredo do webhook" })).toHaveValue("segredo-novo-456");
  });

  test("exclui depois de confirmar", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks");
    await page.getByRole("button", { name: "Ações do webhook Utmify" }).click();
    await page.getByRole("menuitem", { name: "Excluir" }).click();
    await expect(page.getByRole("dialog", { name: "Excluir webhook" })).toBeVisible();
    await page.getByRole("dialog").getByRole("button", { name: "Excluir" }).click();
    await expect(page.getByText("Webhook excluído")).toBeVisible();
    expect(pedidos.some(item => item.metodo === "DELETE" && item.url.pathname.endsWith("/w2"))).toBe(true);
  });

  test("Ver logs abre a tela de logs do webhook com filtros, status e paginação", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks");
    await page.getByRole("button", { name: "Ações do webhook Rangu Produção" }).click();
    await page.getByRole("menuitem", { name: "Ver logs" }).click();
    await expect(page).toHaveURL(/\/apps\/webhooks\/w1$/);
    await expect(page.getByRole("heading", { name: "Rangu Produção - Rangu" })).toBeVisible();
    for (const coluna of ["Data", "Evento", "ID venda", "Status"]) await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    await expect(page.getByRole("cell", { name: "Carrinho abandonado", exact: true })).toBeVisible();
    await expect(page.getByRole("cell", { name: "ABC1234", exact: true })).toBeVisible();
    await expect(page.getByText("Falhou", { exact: true })).toBeVisible();
    await expect(page.getByText("Sucesso", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: /Últimos 7 dias/ })).toBeVisible();
    await expect(page.getByRole("button", { name: "Reenviar webhooks" })).toBeDisabled();
    expect(pedidos.filter(item => item.url.pathname.endsWith("/logs")).at(-1)?.url.searchParams.get("from")).toMatch(/^\d{4}-\d{2}-\d{2}$/);

    await page.getByRole("combobox").selectOption("cart");
    await expect.poll(() => pedidos.filter(item => item.url.pathname.endsWith("/logs")).at(-1)?.url.searchParams.getAll("event")).toEqual(["CART.ABANDONED"]);
    await page.getByRole("searchbox").fill("ABC1234");
    await expect.poll(() => pedidos.filter(item => item.url.pathname.endsWith("/logs")).at(-1)?.url.searchParams.get("q")).toBe("ABC1234");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("reenvia um envio pelo menu e vários pela seleção", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks/w1");
    await page.getByRole("button", { name: /Ações do envio de Carrinho abandonado/ }).click();
    await page.getByRole("menuitem", { name: "Reenviar" }).click();
    await expect(page.getByText("Webhook reenviado")).toBeVisible();
    expect(pedidos.some(item => item.url.pathname.endsWith("/w1/logs/e1/resend"))).toBe(true);

    await page.getByRole("checkbox", { name: "Selecionar todos os envios" }).check();
    await page.getByRole("button", { name: "Reenviar webhooks" }).click();
    await expect(page.getByText("2 webhooks reenviados")).toBeVisible();
    expect(pedidos.filter(item => item.url.pathname.endsWith("/logs/resend")).at(-1)?.corpo).toEqual({ eventIds: ["e1", "e2"] });
  });

  test("Ver logs mostra os detalhes com requisição, resposta e reenvio", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/apps/webhooks/w1");
    await page.getByRole("button", { name: /Ações do envio de Carrinho abandonado/ }).click();
    await page.getByRole("menuitem", { name: "Ver logs" }).click();
    const janela = page.getByRole("dialog", { name: "Detalhes" });
    await expect(janela).toBeVisible();
    await expect(janela.getByText("Carrinho abandonado")).toBeVisible();
    await expect(janela.getByText("https://api.rangu.app/api/webhooks/paysi")).toBeVisible();
    await expect(janela.getByText("Falhou")).toBeVisible();
    await expect(janela.getByText("3 tentativas de envio.")).toBeVisible();
    await expect(janela.getByRole("region", { name: "Requisição" })).toContainText('"type": "CART.ABANDONED"');
    await expect(janela.getByRole("region", { name: "Resposta" })).toContainText("<html>erro</html>");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
    await janela.getByRole("button", { name: "Reenviar webhook" }).click();
    await expect(janela.getByText("Webhook reenviado.")).toBeVisible();
    expect(pedidos.some(item => item.url.pathname.endsWith("/w1/logs/e1/resend"))).toBe(true);
    await janela.getByRole("button", { name: "Fechar", exact: true }).last().click();
    await expect(janela).toBeHidden();
  });

  test("estados vazios e a documentação de webhooks", async ({ page }) => {
    await preparar(page, { hooks: [], logs: [] });
    await page.goto("/apps/webhooks");
    await expect(page.getByText("Crie um webhook para receber avisos")).toBeVisible();
    await page.goto("/apps/webhooks/w1");
    await expect(page.getByText("Nenhum evento foi enviado")).toBeVisible();
    await page.goto("/ajuda/webhooks");
    await expect(page.getByRole("heading", { name: "Webhooks da Paysi" })).toBeVisible();
    await expect(page.getByText("X-Paysi-Signature").first()).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });
});
