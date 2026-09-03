const formatador = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function formatarCentavos(cents: number): string {
  return formatador.format(cents / 100);
}
