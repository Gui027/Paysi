/**
 * Aceite versionado: o hash muda sempre que o texto vigente (termsUrl) muda,
 * sem exigir um número de versão dedicado no contrato do checkout.
 */
export async function calcularTermosHash(termsUrl: string): Promise<string> {
  const bytes = new TextEncoder().encode(termsUrl);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const hex = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, "0")).join("");
  return `sha256:${hex}`;
}
