const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

/** Somente apresentação. Valores e cálculos sempre vêm da API. */
export function formatarCentavos(valorCentavos: number): string {
  return moeda.format(valorCentavos / 100);
}

