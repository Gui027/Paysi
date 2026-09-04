import { apiRequest } from "./api";

export type KycStatus = "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";

export type KycRequirement = {
  code: string;
  label: string;
  status: string;
  reason: string | null;
  estimatedAt: string | null;
};

export type KycView = {
  accountId: string;
  kycStatus: KycStatus;
  providerUrl: string | null;
  requirements: KycRequirement[];
};

export const kycStatusLabel: Record<KycStatus, string> = {
  PENDING: "Verificação não iniciada",
  SUBMITTED: "Verificação em análise",
  APPROVED: "Verificado",
  REJECTED: "Verificação recusada",
};

const requirementStatusLabels: Record<string, string> = {
  APPROVED: "Aprovado",
  OK: "Aprovado",
  COMPLETED: "Aprovado",
  DONE: "Aprovado",
  REJECTED: "Recusado",
  FAILED: "Recusado",
  INVALID: "Recusado",
  PENDING: "Pendente",
  IN_REVIEW: "Em análise",
  SUBMITTED: "Em análise",
};

export function requirementStatusLabel(status: string): string {
  return requirementStatusLabels[status.toUpperCase()] ?? status;
}

export function getKyc() {
  return apiRequest<KycView>("/v1/accounts/me");
}

export function startKyc() {
  return apiRequest<KycView>("/v1/accounts/me/kyc", { method: "POST" });
}
