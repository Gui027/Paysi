import { PersonType } from "./checkout.js";

export function apenasDigitos(value: string): string {
  return value.replace(/\D/g, "");
}

export function formatarDocumento(value: string, personType: PersonType): string {
  const digits = apenasDigitos(value).slice(0, personType === "PF" ? 11 : 14);
  if (personType === "PF") {
    return digits
      .replace(/^(\d{3})(\d)/, "$1.$2")
      .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
      .replace(/\.(\d{3})(\d)/, ".$1-$2");
  }
  return digits
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\/\d{4})(\d)/, "$1-$2");
}

/** Confere o dígito verificador. Mesmo algoritmo de com.paysi.identity.domain.TaxId (backend). */
export function documentoValido(value: string, personType: PersonType): boolean {
  const digits = apenasDigitos(value);
  return personType === "PF" ? digits.length === 11 && isValidCpf(digits) : digits.length === 14 && isValidCnpj(digits);
}

function isValidCpf(cpf: string): boolean {
  if (allSameDigit(cpf)) return false;
  if (cpfCheckDigit(cpf, 9, 10) !== digitAt(cpf, 9)) return false;
  return cpfCheckDigit(cpf, 10, 11) === digitAt(cpf, 10);
}

function cpfCheckDigit(cpf: string, length: number, firstWeight: number): number {
  let sum = 0;
  for (let i = 0; i < length; i++) sum += digitAt(cpf, i) * (firstWeight - i);
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}

function isValidCnpj(cnpj: string): boolean {
  if (allSameDigit(cnpj)) return false;
  const firstWeights = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  if (cnpjCheckDigit(cnpj, 12, firstWeights) !== digitAt(cnpj, 12)) return false;
  const secondWeights = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2];
  return cnpjCheckDigit(cnpj, 13, secondWeights) === digitAt(cnpj, 13);
}

function cnpjCheckDigit(cnpj: string, length: number, weights: number[]): number {
  let sum = 0;
  for (let i = 0; i < length; i++) sum += digitAt(cnpj, i) * weights[i];
  const remainder = sum % 11;
  return remainder < 2 ? 0 : 11 - remainder;
}

function allSameDigit(digits: string): boolean {
  return digits.split("").every(char => char === digits[0]);
}

function digitAt(digits: string, index: number): number {
  return digits.charCodeAt(index) - "0".charCodeAt(0);
}
