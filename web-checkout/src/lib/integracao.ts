/**
 * Integração com o sistema do vendedor (SaaS, landing page): o link do checkout pode trazer
 * `?ref=` (identificador do cliente no sistema dele), `?email=` e `?name=` para pré-preencher.
 * Nada disso muda preço: o valor vem sempre da oferta, lida no servidor.
 */
export type ParametrosIntegracao = {
  reference: string | null;
  email: string | null;
  name: string | null;
};

const REFERENCE_MAX = 128;
const NAME_MAX = 200;
const EMAIL_MAX = 320;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function lerParametrosIntegracao(search: string): ParametrosIntegracao {
  const params = new URLSearchParams(search);
  const reference = params.get("ref")?.trim() ?? "";
  const email = params.get("email")?.trim() ?? "";
  const name = params.get("name")?.trim() ?? "";
  return {
    reference: reference && reference.length <= REFERENCE_MAX ? reference : null,
    email: email && email.length <= EMAIL_MAX && EMAIL_PATTERN.test(email) ? email : null,
    name: name && name.length <= NAME_MAX ? name : null,
  };
}

/** Só https (ou http em localhost); qualquer outra coisa não vira link de retorno. */
export function montarUrlRetorno(returnUrl: string | null | undefined, reference: string | null): string | null {
  if (!returnUrl) return null;
  let url: URL;
  try {
    url = new URL(returnUrl);
  } catch {
    return null;
  }
  const local = url.hostname === "localhost" || url.hostname === "127.0.0.1";
  if (url.protocol !== "https:" && !(url.protocol === "http:" && local)) return null;
  if (url.username || url.password) return null;
  url.searchParams.set("paysi_status", "approved");
  if (reference) url.searchParams.set("ref", reference);
  return url.toString();
}
