const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

/** Somente apresentação. Valores e cálculos sempre vêm da API. */
export function formatarCentavos(valorCentavos: number): string {
  return moeda.format(valorCentavos / 100);
}

/** Converte entrada monetária brasileira em centavos sem usar ponto flutuante. */
export function lerCentavos(valor: string): number | null {
  const bruto = valor.trim().replace(/^R\$\s*/i, "").replace(/\s/g, "");
  if (!bruto) return null;
  const normalizado = bruto.includes(",") ? bruto.replace(/\./g, "").replace(",", ".") : bruto;
  if (!/^\d+(\.\d{1,2})?$/.test(normalizado)) return null;
  const [inteiro, fracao = ""] = normalizado.split(".");
  const centavos = BigInt(inteiro) * 100n + BigInt(fracao.padEnd(2, "0"));
  return centavos <= BigInt(Number.MAX_SAFE_INTEGER) ? Number(centavos) : null;
}
