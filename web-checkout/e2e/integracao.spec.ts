import { test, expect } from "@playwright/test";
import {
  OFERTA_SLUG,
  mockOferta,
  mockCobranca,
  COBRANCA_CARTAO_APROVADA,
  mockTokenizacaoCartao,
  preencherTitularDoCartao,
  aceitarTermos,
} from "./fixtures";

test.describe("checkout — integração com o sistema do vendedor", () => {
  test("pré-preenche nome e e-mail do link e envia o ref no pedido", async ({ page }) => {
    await mockOferta(page);
    let corpo: Record<string, unknown> | null = null;
    await page.route(`**/v1/checkout/${OFERTA_SLUG}/orders`, async (route) => {
      corpo = route.request().postDataJSON();
      await route.fulfill({ json: { orderId: "order_int_1", status: "CREATED" } });
    });
    await mockTokenizacaoCartao(page);
    await mockCobranca(page, COBRANCA_CARTAO_APROVADA);
    await page.goto(`/${OFERTA_SLUG}?ref=user_123&email=ana%40exemplo.com&name=Ana%20Souza`);

    await expect(page.getByLabel(/nome completo/i)).toHaveValue("Ana Souza");
    await expect(page.getByLabel(/e-mail/i)).toHaveValue("ana@exemplo.com");
    await page.getByLabel(/cpf/i).fill("39053344705");
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("ANA SOUZA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /pagamento aprovado/i })).toBeVisible();
    expect(corpo).toMatchObject({ reference: "user_123" });
  });

  test("sem parâmetros, o pedido não leva reference", async ({ page }) => {
    await mockOferta(page);
    let corpo: Record<string, unknown> | null = null;
    await page.route(`**/v1/checkout/${OFERTA_SLUG}/orders`, async (route) => {
      corpo = route.request().postDataJSON();
      await route.fulfill({ json: { orderId: "order_int_2", status: "CREATED" } });
    });
    await mockTokenizacaoCartao(page);
    await mockCobranca(page, COBRANCA_CARTAO_APROVADA);
    await page.goto(`/${OFERTA_SLUG}`);
    await page.getByLabel(/nome completo/i).fill("Maria Compradora");
    await page.getByLabel(/e-mail/i).fill("maria@example.com");
    await page.getByLabel(/cpf/i).fill("39053344705");
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("MARIA COMPRADORA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();
    await expect(page.getByRole("heading", { name: /pagamento aprovado/i })).toBeVisible();
    expect(corpo).not.toHaveProperty("reference");
  });

  test("com URL de retorno, oferece voltar ao sistema do vendedor com o ref", async ({ page }) => {
    await mockOferta(page, { returnUrl: "https://app.exemplo.com/obrigado" });
    await page.route(`**/v1/checkout/${OFERTA_SLUG}/orders`, (route) => route.fulfill({ json: { orderId: "order_int_3", status: "CREATED" } }));
    await mockTokenizacaoCartao(page);
    await mockCobranca(page, COBRANCA_CARTAO_APROVADA);
    await page.route("https://app.exemplo.com/**", (route) => route.fulfill({ body: "ok" }));
    await page.goto(`/${OFERTA_SLUG}?ref=user_123`);
    await page.getByLabel(/nome completo/i).fill("Maria Compradora");
    await page.getByLabel(/e-mail/i).fill("maria@example.com");
    await page.getByLabel(/cpf/i).fill("39053344705");
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("MARIA COMPRADORA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    const voltar = page.getByRole("link", { name: "Voltar agora" });
    await expect(voltar).toHaveAttribute("href", "https://app.exemplo.com/obrigado?paysi_status=approved&ref=user_123");
    await voltar.click();
    await expect(page).toHaveURL(/app\.exemplo\.com\/obrigado\?paysi_status=approved&ref=user_123/);
  });
});
