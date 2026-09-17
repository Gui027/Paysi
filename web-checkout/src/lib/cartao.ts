import { apenasDigitos } from "./documento.js";

export function formatarNumeroCartao(value: string): string {
  return apenasDigitos(value).slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 ").trim();
}

export function formatarValidade(value: string): string {
  const digits = apenasDigitos(value).slice(0, 4);
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
}

/** Algoritmo de Luhn — mesma verificação usada por qualquer adquirente. */
export function numeroCartaoValido(value: string): boolean {
  const digits = apenasDigitos(value);
  if (digits.length < 13 || digits.length > 19) return false;
  let sum = 0;
  let double = false;
  for (let i = digits.length - 1; i >= 0; i--) {
    let digit = digits.charCodeAt(i) - 48;
    if (double) {
      digit *= 2;
      if (digit > 9) digit -= 9;
    }
    sum += digit;
    double = !double;
  }
  return sum % 10 === 0;
}

export function validadeValida(value: string, agora: Date = new Date()): boolean {
  const digits = apenasDigitos(value);
  if (digits.length !== 4) return false;
  const mes = Number(digits.slice(0, 2));
  const ano = 2000 + Number(digits.slice(2));
  if (mes < 1 || mes > 12) return false;
  const expiraEm = new Date(ano, mes, 1);
  return expiraEm > agora;
}

export function cvvValido(value: string): boolean {
  const digits = apenasDigitos(value);
  return digits.length === 3 || digits.length === 4;
}

/**
 * Sem SDK real do provedor neste ambiente: simulamos a tokenização que ele faria
 * no navegador. O número e o CVV nunca saem deste módulo — só o token opaco é
 * enviado ao backend da Paysi (CardPaymentCommand.cardToken).
 */
export function gerarTokenCartao(): string {
  return `tok_${crypto.randomUUID()}`;
}
