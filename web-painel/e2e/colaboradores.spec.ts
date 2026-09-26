import { test, expect, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockSessao, mockDashboardVazio } from "./fixtures";

const PENDENTE = { id: "c1", email: "guilhermeeg1234@gmail.com", status: "PENDING", permissions: ["ALL"], invitedAt: "2026-09-26T14:02:00Z" };
const ATIVO = { id: "c2", email: "ativo@exemplo.com", status: "ACTIVE", permissions: ["sales", "finance"], invitedAt: "2026-09-20T14:02:00Z" };

function pagina(items: unknown[]) {
  return { items, page: 1, size: 10, total: items.length, totalPages: 1 };
}

async function preparar(page: Page, itens: unknown[] = [PENDENTE, ATIVO]) {
  const pedidos: { metodo: string; url: URL; corpo: unknown }[] = [];
  await mockSessao(page);
  await mockDashboardVazio(page);
  await page.route("**/api/v1/collaborators**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    pedidos.push({ metodo: request.method(), url, corpo: request.postData() ? JSON.parse(request.postData()!) : null });
    if (request.method() === "GET") return route.fulfill({ json: pagina(itens) });
    if (request.method() === "POST" && url.pathname.endsWith("/resend")) return route.fulfill({ status: 204 });
    if (request.method() === "DELETE") return route.fulfill({ status: 204 });
    if (request.method() === "POST" && String(request.postData()).includes("dono@exemplo.com")) {
      return route.fulfill({ status: 409, json: { code: "COLLABORATOR_ALREADY_EXISTS", message: "Esse usuário já é colaborador ou dono da conta", field: "email" } });
    }
    return route.fulfill({ json: PENDENTE });
  });
  return pedidos;
}

test.describe("colaboradores", () => {
  test("lista com status, data do convite, busca e passa no axe", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/colaboradores");
    await expect(page.getByRole("heading", { name: "Colaboradores" })).toBeVisible();
    for (const coluna of ["E-mail", "Status", "Data do convite"]) await expect(page.getByRole("columnheader", { name: coluna })).toBeVisible();
    await expect(page.getByRole("cell", { name: PENDENTE.email, exact: true })).toBeVisible();
    await expect(page.getByText("Pendente", { exact: true })).toBeVisible();
    await expect(page.getByText("Ativo", { exact: true })).toBeVisible();
    await page.getByRole("searchbox").fill("gui");
    await expect.poll(() => pedidos.at(-1)?.url.searchParams.get("q")).toBe("gui");
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });

  test("adiciona com acesso total, e mostra o erro quando já é colaborador ou dono", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/colaboradores");
    await page.getByRole("button", { name: "Adicionar colaborador" }).click();
    await expect(page.getByLabel("Acesso total")).toBeChecked();
    await page.getByRole("textbox", { name: "E-mail" }).fill("dono@exemplo.com");
    await page.getByRole("dialog").getByRole("button", { name: "Adicionar colaborador" }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("já é colaborador ou dono da conta");

    await page.getByRole("textbox", { name: "E-mail" }).fill("novo@exemplo.com");
    await page.getByRole("dialog").getByRole("button", { name: "Adicionar colaborador" }).click();
    await expect(page.getByText("Colaborador adicionado")).toBeVisible();
    const post = pedidos.filter(item => item.metodo === "POST").at(-1);
    expect(post?.corpo).toEqual({ email: "novo@exemplo.com", permissions: ["ALL"] });
  });

  test("sem acesso total escolhe as áreas e valida antes de enviar", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/colaboradores");
    await page.getByRole("button", { name: "Adicionar colaborador" }).click();
    await page.getByRole("textbox", { name: "E-mail" }).fill("novo@exemplo.com");
    await page.getByLabel("Acesso total").uncheck();
    await page.getByRole("dialog").getByRole("button", { name: "Adicionar colaborador" }).click();
    await expect(page.getByRole("dialog").getByRole("alert")).toContainText("Escolha ao menos uma permissão");
    await page.getByLabel("Vendas").check();
    await page.getByLabel("Financeiro").check();
    await page.getByRole("dialog").getByRole("button", { name: "Adicionar colaborador" }).click();
    await expect(page.getByText("Colaborador adicionado")).toBeVisible();
    expect(pedidos.filter(item => item.metodo === "POST").at(-1)?.corpo).toEqual({ email: "novo@exemplo.com", permissions: ["sales", "finance"] });
  });

  test("menu da linha: editar, reenviar convite e remover acesso", async ({ page }) => {
    const pedidos = await preparar(page);
    await page.goto("/colaboradores");

    await page.getByRole("button", { name: `Ações para ${ATIVO.email}` }).click();
    await expect(page.getByRole("menuitem", { name: "Reenviar convite" })).toHaveCount(0);
    await page.getByRole("menuitem", { name: "Editar" }).click();
    await expect(page.getByLabel("Acesso total")).not.toBeChecked();
    await expect(page.getByLabel("Vendas")).toBeChecked();
    await page.getByLabel("Assinaturas").check();
    await page.getByRole("button", { name: "Salvar" }).click();
    await expect(page.getByText("Permissões atualizadas")).toBeVisible();
    expect(pedidos.filter(item => item.metodo === "PUT").at(-1)?.corpo).toEqual({ permissions: ["sales", "finance", "subscriptions"] });

    await page.getByRole("button", { name: `Ações para ${PENDENTE.email}` }).click();
    await page.getByRole("menuitem", { name: "Reenviar convite" }).click();
    await expect(page.getByText("Convite reenviado")).toBeVisible();
    expect(pedidos.some(item => item.url.pathname.endsWith("/c1/resend"))).toBe(true);

    await page.getByRole("button", { name: `Ações para ${PENDENTE.email}` }).click();
    await page.getByRole("menuitem", { name: "Remover acesso" }).click();
    await expect(page.getByRole("dialog", { name: "Remover acesso" })).toBeVisible();
    await page.getByRole("dialog").getByRole("button", { name: "Remover acesso" }).click();
    await expect(page.getByText("Acesso removido")).toBeVisible();
    expect(pedidos.some(item => item.metodo === "DELETE" && item.url.pathname.endsWith("/c1"))).toBe(true);
  });

  test("lista vazia mostra a orientação e o menu lateral leva à página", async ({ page }) => {
    await preparar(page, []);
    await page.goto("/inicio");
    await page.getByRole("link", { name: "Colaboradores" }).click();
    await expect(page).toHaveURL(/\/colaboradores$/);
    await expect(page.getByText("Convide pessoas para ajudar")).toBeVisible();
  });
});
