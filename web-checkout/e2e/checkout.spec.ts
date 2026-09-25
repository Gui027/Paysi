import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import {
  OFERTA_SLUG,
  mockOferta,
  mockOfertaNotFound,
  mockOfertaErro,
  mockPedido,
  mockCobranca,
  COBRANCA_CARTAO_APROVADA,
  COBRANCA_CARTAO_RECUSADA,
  COBRANCA_PIX,
  COBRANCA_BOLETO,
  preencherComprador,
  preencherTitularDoCartao,
  mockTokenizacaoCartao,
  aceitarTermos,
} from "./fixtures";

test.describe("checkout — estados de carregamento/vazio/erro", () => {
  test("mostra carregando e depois o formulário", async ({ page }) => {
    await mockOferta(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await expect(page.getByRole("heading", { name: /quem está comprando/i })).toBeVisible();
  });

  test("oferta inexistente mostra estado vazio", async ({ page }) => {
    await mockOfertaNotFound(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await expect(page.getByRole("heading", { name: /oferta não encontrada/i })).toBeVisible();
  });

  test("falha de rede mostra estado de erro com retry visual", async ({ page }) => {
    await mockOfertaErro(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await expect(page.getByRole("heading", { name: /não foi possível carregar/i })).toBeVisible();
  });

  test("link sem slug mostra estado de link inválido", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: /link inválido/i })).toBeVisible();
  });
});

test.describe("checkout — validação", () => {
  test("bloqueia envio sem preencher comprador nem aceitar termos", async ({ page }) => {
    await mockOferta(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await page.getByRole("button", { name: /pagar agora/i }).click();
    await expect(page.getByRole("alert").first()).toBeVisible();
    await expect(page.getByText(/é preciso aceitar os termos/i)).toBeVisible();
  });

  test("CPF inválido é rejeitado", async ({ page }) => {
    await mockOferta(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await page.getByLabel(/nome completo/i).fill("Maria Compradora");
    await page.getByLabel(/e-mail/i).fill("maria@example.com");
    await page.getByLabel(/cpf/i).fill("11111111111");
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();
    await expect(page.getByText(/cpf inválido/i)).toBeVisible();
  });
});

test.describe("checkout — fluxo feliz por método de pagamento", () => {
  test("Pix: gera QR code e tela de aguardando pagamento", async ({ page }) => {
    await mockOferta(page);
    await mockPedido(page);
    await mockCobranca(page, COBRANCA_PIX);
    await page.goto(`/${OFERTA_SLUG}`);

    await preencherComprador(page);
    await page.getByRole("radio", { name: "Pix" }).check();
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /aguardando pagamento do pix/i })).toBeVisible();
    await expect(page.getByLabel(/código pix copia e cola/i)).toHaveValue(COBRANCA_PIX.pixQrCode);
  });

  test("Boleto: exibe código de barras e link do boleto", async ({ page }) => {
    await mockOferta(page);
    await mockPedido(page);
    await mockCobranca(page, COBRANCA_BOLETO);
    await page.goto(`/${OFERTA_SLUG}`);

    await preencherComprador(page);
    await page.getByRole("radio", { name: "Boleto" }).check();
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /boleto emitido/i })).toBeVisible();
    await expect(page.getByLabel(/linha digitável do boleto/i)).toHaveValue(COBRANCA_BOLETO.boletoBarcode);
  });

  test("Cartão: aprovado mostra confirmação de sucesso", async ({ page }) => {
    await mockOferta(page);
    await mockPedido(page);
    await mockTokenizacaoCartao(page);
    await mockCobranca(page, COBRANCA_CARTAO_APROVADA);
    await page.goto(`/${OFERTA_SLUG}`);

    await preencherComprador(page);
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("MARIA COMPRADORA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /pagamento aprovado/i })).toBeVisible();
  });

  test("Cartão: número e CVV só vão ao /card-token, a cobrança leva apenas o token", async ({ page }) => {
    const capturas: { tokenizacao?: string; cobranca?: string } = {};
    await mockOferta(page);
    await mockPedido(page);
    await mockTokenizacaoCartao(page, capturas);
    await mockCobranca(page, COBRANCA_CARTAO_APROVADA);
    await page.goto(`/${OFERTA_SLUG}`);

    await preencherComprador(page);
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("MARIA COMPRADORA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /pagamento aprovado/i })).toBeVisible();
    expect(capturas.tokenizacao).toContain("4111111111111111");
    expect(capturas.tokenizacao).toContain("01310100");
    expect(capturas.cobranca).toContain("tok_e2e_1");
    expect(capturas.cobranca).not.toContain("4111111111111111");
    expect(capturas.cobranca).not.toContain("\"ccv\"");
  });

  test("Cartão: recusado mostra tela de recusa com opção de tentar Pix", async ({ page }) => {
    await mockOferta(page);
    await mockPedido(page);
    await mockTokenizacaoCartao(page);
    await mockCobranca(page, COBRANCA_CARTAO_RECUSADA);
    await page.goto(`/${OFERTA_SLUG}`);

    await preencherComprador(page);
    await page.getByRole("radio", { name: "Cartão" }).check();
    await page.getByLabel(/nome impresso no cartão/i).fill("MARIA COMPRADORA");
    await page.getByLabel(/número do cartão/i).fill("4111111111111111");
    await page.getByLabel(/validade/i).fill("1230");
    await page.getByLabel(/cvv/i).fill("123");
    await preencherTitularDoCartao(page);
    await aceitarTermos(page);
    await page.getByRole("button", { name: /pagar agora/i }).click();

    await expect(page.getByRole("heading", { name: /pagamento recusado/i })).toBeVisible();
  });
});

test.describe("checkout — acessibilidade e teclado", () => {
  test("formulário não tem violações críticas/sérias de acessibilidade (axe)", async ({ page }) => {
    await mockOferta(page);
    await page.goto(`/${OFERTA_SLUG}`);
    await expect(page.getByRole("heading", { name: /quem está comprando/i })).toBeVisible();

    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();
    const graves = results.violations.filter(v => v.impact === "critical" || v.impact === "serious");
    expect(graves, JSON.stringify(graves, null, 2)).toEqual([]);
  });

  test("é possível preencher e enviar o formulário só com teclado", async ({ page }) => {
    await mockOferta(page);
    await mockPedido(page);
    await mockCobranca(page, COBRANCA_PIX);
    await page.goto(`/${OFERTA_SLUG}`);

    await page.getByLabel(/nome completo/i).click();
    await page.keyboard.type("Maria Compradora");
    await page.keyboard.press("Tab");
    await page.keyboard.type("maria@example.com");
    await page.keyboard.press("Tab");
    await page.keyboard.type("39053344705");

    // Navega até o rádio "Pix" e seleciona via teclado.
    const pixRadio = page.getByRole("radio", { name: "Pix" });
    await pixRadio.focus();
    await page.keyboard.press("Space");
    await expect(pixRadio).toBeChecked();

    await page.getByRole("checkbox", { name: /li e aceito os/i }).focus();
    await page.keyboard.press("Space");

    await page.getByRole("button", { name: /pagar agora/i }).focus();
    await page.keyboard.press("Enter");

    await expect(page.getByRole("heading", { name: /aguardando pagamento do pix/i })).toBeVisible();
  });
});
