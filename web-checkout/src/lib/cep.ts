import { apenasDigitos } from "./documento.js";

export type EnderecoPorCep = {
  street: string;
  district: string;
  city: string;
  state: string;
};

type ViaCepResponse = {
  erro?: boolean;
  logradouro?: string;
  bairro?: string;
  localidade?: string;
  uf?: string;
};

/** Consulta somente CEPs completos. Os campos retornados são usados em segundo plano no pedido. */
export async function consultarCep(value: string, signal?: AbortSignal): Promise<EnderecoPorCep> {
  const cep = apenasDigitos(value);
  if (cep.length !== 8) throw new Error("CEP_INVALIDO");
  const response = await fetch(`https://viacep.com.br/ws/${cep}/json/`, { signal });
  if (!response.ok) throw new Error("CEP_INDISPONIVEL");
  const data = await response.json() as ViaCepResponse;
  if (data.erro || !data.logradouro || !data.bairro || !data.localidade || !data.uf) {
    throw new Error("CEP_NAO_ENCONTRADO");
  }
  return {
    street: data.logradouro,
    district: data.bairro,
    city: data.localidade,
    state: data.uf,
  };
}
