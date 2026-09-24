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

export function formatarCep(value: string): string {
  const digits = apenasDigitos(value).slice(0, 8);
  return digits.length > 5 ? `${digits.slice(0, 5)}-${digits.slice(5)}` : digits;
}

export function cepValido(value: string): boolean {
  return apenasDigitos(value).length === 8;
}

export function telefoneValido(value: string): boolean {
  const digits = apenasDigitos(value);
  return digits.length === 10 || digits.length === 11;
}

/** Dados que o backend recebe para trocar o cartão por um token do provedor (POST /card-token). */
export type DadosCartao = {
  holderName: string;
  number: string;
  expiryMonth: string;
  expiryYear: string;
  ccv: string;
  postalCode: string;
  addressNumber: string;
  phone: string;
};

export type CartaoDigitado = {
  nome: string;
  numero: string;
  validade: string;
  cvv: string;
  cep: string;
  numeroEndereco: string;
  telefone: string;
};

export function cartaoCompleto(c: CartaoDigitado, agora: Date = new Date()): boolean {
  return numeroCartaoValido(c.numero) && validadeValida(c.validade, agora) && cvvValido(c.cvv)
    && c.nome.trim().length > 1 && cepValido(c.cep) && c.numeroEndereco.trim().length > 0
    && telefoneValido(c.telefone);
}

/** "MM/AA" vira mês e ano de quatro dígitos, como a API espera; só dígitos nos demais campos. */
export function montarDadosCartao(c: CartaoDigitado): DadosCartao {
  const validade = apenasDigitos(c.validade);
  return {
    holderName: c.nome.trim(),
    number: apenasDigitos(c.numero),
    expiryMonth: String(Number(validade.slice(0, 2))),
    expiryYear: `20${validade.slice(2, 4)}`,
    ccv: apenasDigitos(c.cvv),
    postalCode: apenasDigitos(c.cep),
    addressNumber: c.numeroEndereco.trim(),
    phone: apenasDigitos(c.telefone),
  };
}
