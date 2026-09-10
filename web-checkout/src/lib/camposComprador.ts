import { documentoValido } from "./documento.js";
import { cepValido } from "./mascaras.js";
import { PersonType } from "./checkout.js";

export type ChaveCampo =
  | "name" | "email" | "taxId" | "legalName" | "municipalReg"
  | "address.zipCode" | "address.street" | "address.number" | "address.complement"
  | "address.district" | "address.city" | "address.state";

const CAMPO_LABEL: Record<ChaveCampo, string> = {
  name: "Nome completo",
  email: "E-mail",
  taxId: "Documento",
  legalName: "Razão social",
  municipalReg: "Inscrição municipal",
  "address.zipCode": "CEP",
  "address.street": "Rua",
  "address.number": "Número",
  "address.complement": "Complemento",
  "address.district": "Bairro",
  "address.city": "Cidade",
  "address.state": "Estado (UF)",
};

export function rotuloCampo(key: ChaveCampo, personType: PersonType): string {
  if (key === "taxId") return personType === "PF" ? "CPF" : "CNPJ";
  return CAMPO_LABEL[key];
}

/** Aceita apenas chaves reconhecidas pelo contrato do checkout; ignora "personType" (é o seletor, não um campo de texto). */
export function comoChaveCampo(field: string): ChaveCampo | null {
  return field in CAMPO_LABEL ? (field as ChaveCampo) : null;
}

export function validarCampo(key: ChaveCampo, value: string, personType: PersonType): string | undefined {
  const trimmed = value.trim();
  switch (key) {
    case "email":
      return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed) ? undefined : "Informe um e-mail válido.";
    case "taxId":
      return documentoValido(value, personType)
        ? undefined
        : (personType === "PF" ? "CPF inválido." : "CNPJ inválido.");
    case "address.zipCode":
      return cepValido(value) ? undefined : "CEP inválido.";
    case "address.state":
      return /^[A-Za-z]{2}$/.test(trimmed) ? undefined : "Use a sigla do estado (UF).";
    default:
      return trimmed ? undefined : `Informe: ${CAMPO_LABEL[key].toLowerCase()}.`;
  }
}
