import { apenasDigitos } from "./documento.js";

export function formatarCep(value: string): string {
  const digits = apenasDigitos(value).slice(0, 8);
  return digits.replace(/^(\d{5})(\d)/, "$1-$2");
}

export function cepValido(value: string): boolean {
  return apenasDigitos(value).length === 8;
}
