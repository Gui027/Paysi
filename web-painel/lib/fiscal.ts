import { apiRequest, ApiRequestError } from "./api";

export type TaxRegime = "SIMPLES" | "PRESUMIDO" | "REAL";

export type FiscalProfile = {
  municipalityCode: string;
  municipalRegistration: string;
  serviceItem: string;
  taxBps: number;
  taxRegime: TaxRegime;
  validated: boolean;
};

export type FiscalProfileInput = {
  municipalityCode: string;
  municipalRegistration: string;
  serviceItem: string;
  taxBps: number;
  taxRegime: TaxRegime;
  credentialRef: string;
};

export const taxRegimeLabel: Record<TaxRegime, string> = {
  SIMPLES: "Simples Nacional",
  PRESUMIDO: "Lucro Presumido",
  REAL: "Lucro Real",
};

export async function getFiscalProfile(): Promise<FiscalProfile | null> {
  try {
    return await apiRequest<FiscalProfile>("/v1/fiscal/profile");
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) return null;
    throw error;
  }
}

export function saveFiscalProfile(input: FiscalProfileInput) {
  return apiRequest<FiscalProfile>("/v1/fiscal/profile", { method: "PUT", body: JSON.stringify(input) });
}
