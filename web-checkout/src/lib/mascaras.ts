import { apenasDigitos } from "./documento.js";

export function formatarCep(value: string): string {
  const digits = apenasDigitos(value).slice(0, 8);
  return digits.replace(/^(\d{5})(\d)/, "$1-$2");
}

export function cepValido(value: string): boolean {
  return apenasDigitos(value).length === 8;
}

/** Máscara progressiva para telefone brasileiro com DDD (fixo ou celular). */
export function formatarTelefone(value: string): string {
  const digits = apenasDigitos(value).slice(0, 11);
  if (digits.length === 0) return "";
  if (digits.length < 3) return `(${digits}`;

  const ddd = digits.slice(0, 2);
  const numero = digits.slice(2);
  if (numero.length <= 4) return `(${ddd}) ${numero}`;

  const tamanhoPrefixo = numero.length <= 8 ? 4 : 5;
  return `(${ddd}) ${numero.slice(0, tamanhoPrefixo)}-${numero.slice(tamanhoPrefixo)}`;
}
