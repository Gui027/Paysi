const STORAGE_KEY = "paysi.checkout.idempotency-key";

export function obterChaveDeIdempotencia(): string {
  const existing = sessionStorage.getItem(STORAGE_KEY);
  if (existing) return existing;
  const created = crypto.randomUUID();
  sessionStorage.setItem(STORAGE_KEY, created);
  return created;
}

