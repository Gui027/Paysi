const STORAGE_KEY = "paysi.checkout.idempotency-key";

type Stored = { fingerprint: string; key: string };

/**
 * A chave é atrelada ao conteúdo do pedido: repetir o mesmo envio (retry de rede) reaproveita
 * a chave, mas mudar qualquer dado do formulário gera uma chave nova. Reusar a chave com outro
 * corpo é recusado pelo backend ("A chave já foi usada com outro conteúdo").
 */
export function obterChaveDeIdempotencia(fingerprint: string): string {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    const stored = raw ? (JSON.parse(raw) as Stored) : null;
    if (stored && stored.fingerprint === fingerprint) return stored.key;
  } catch {
    // sessionStorage indisponível ou valor antigo em outro formato: gera uma chave nova.
  }
  const created = crypto.randomUUID();
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ fingerprint, key: created } satisfies Stored));
  } catch {
    // sem storage a chave só não sobrevive a um reload, o que é aceitável.
  }
  return created;
}
