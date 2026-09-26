import { apiRequest, ApiRequestError } from "./api";
import { formatDocumento } from "./vendas";

export type PixKeyType = "CPF" | "CNPJ" | "EMAIL" | "PHONE" | "EVP";
export type PayoutStatus = "REQUESTED" | "SENT" | "CONFIRMED" | "FAILED";

export type FinanceOverview = {
  holder: { name: string; personType: "PF" | "PJ"; taxId: string; country: string };
  balance: { availableCents: number; pendingCents: number; reserveCents: number; debtCents: number };
  pix: { bankAccountId: string; keyType: PixKeyType; key: string; verifiedAt: string } | null;
  kycStatus: "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED";
  mfaEnabled: boolean;
  payoutFeeCents: number;
  minPayoutCents: number;
  mfaThresholdCents: number;
};

export type PayoutRow = {
  id: string;
  createdAt: string;
  amountCents: number;
  status: PayoutStatus;
  destinationName: string;
  pixKeyType: PixKeyType | null;
  pixKey: string | null;
  receiptUrl: string | null;
};

export type PayoutsPage = { items: PayoutRow[]; page: number; size: number; total: number; totalPages: number };

export type Fees = {
  plan: "TRANSACIONAL" | "ESCALA";
  methods: { method: "PIX" | "BOLETO" | "CARD_1" | "CARD_6" | "CARD_12"; feeBps: number; fixedCents: number }[];
  payoutDelayDays: number;
  reserveBps: number;
  reserveDays: number;
};

export type MfaEnrollment = { secret: string; otpauthUri: string; recoveryCodes: string[] };

export const payoutStatusLabel: Record<PayoutStatus, string> = { REQUESTED: "Processando", SENT: "Processando", CONFIRMED: "Sucesso", FAILED: "Falhou" };
export const payoutStatusTone: Record<PayoutStatus, "ok" | "wait" | "bad"> = { REQUESTED: "wait", SENT: "wait", CONFIRMED: "ok", FAILED: "bad" };

export const feeMethodLabel: Record<Fees["methods"][number]["method"], string> = {
  PIX: "Pix",
  BOLETO: "Boleto",
  CARD_1: "Cartão de crédito à vista",
  CARD_6: "Cartão de crédito em 2 a 6x",
  CARD_12: "Cartão de crédito em 7 a 12x",
};

export const planLabel: Record<Fees["plan"], string> = { TRANSACIONAL: "Plano Transacional", ESCALA: "Plano Escala" };

export const kycTitle: Record<FinanceOverview["kycStatus"], string> = {
  APPROVED: "Identidade verificada",
  SUBMITTED: "Verificação em análise",
  REJECTED: "Verificação recusada",
  PENDING: "Verifique a sua identidade",
};

export function getFinance() {
  return apiRequest<FinanceOverview>("/v1/accounts/me/finance");
}

export function listPayouts(page: number) {
  return apiRequest<PayoutsPage>(`/v1/accounts/me/payouts?page=${page}`);
}

export function getFees() {
  return apiRequest<Fees>("/v1/accounts/me/fees");
}

export function savePixKey(pixKey: string, challengeId: string | null) {
  return apiRequest<{ bankAccountId: string; keyType: PixKeyType; key: string }>("/v1/accounts/me/pix-key", {
    method: "PUT",
    headers: challengeId ? { "X-MFA-Challenge-Id": challengeId } : {},
    body: JSON.stringify({ pixKey }),
  });
}

export function convertToCompany(input: { legalName: string; cnpj: string; pixKey: string }, challengeId: string | null) {
  return apiRequest<{ bankAccountId: string }>("/v1/accounts/me/convert-to-company", {
    method: "POST",
    headers: challengeId ? { "X-MFA-Challenge-Id": challengeId } : {},
    body: JSON.stringify(input),
  });
}

export function setupMfa() {
  return apiRequest<MfaEnrollment>("/v1/mfa/setup", { method: "POST" });
}

export function confirmMfaSetup(code: string) {
  return apiRequest<void>("/v1/mfa/setup/confirm", { method: "POST", body: JSON.stringify({ code }) });
}

/** Como a chave aparece para o vendedor: CPF e CNPJ com máscara, o resto como foi guardado. */
export function formatPixKey(key: string | null | undefined, type: PixKeyType | null | undefined): string {
  if (!key) return "";
  return type === "CPF" || type === "CNPJ" ? formatDocumento(key) : key;
}

export function financeError(error: unknown, fallback: string): string {
  if (!(error instanceof ApiRequestError)) return fallback;
  const code = error.problem.code;
  if (code === "MFA_REQUIRED") return "Ative a verificação em duas etapas para continuar.";
  if (code === "MFA_CHALLENGE_INVALID") return "O código de segurança expirou ou já foi usado. Tente novamente.";
  if (code === "TAX_ID_IN_USE") return "Este CNPJ já está cadastrado na Paysi.";
  return error.problem.message ?? fallback;
}

/** Só troca a máscara do CNPJ digitado; a validação de verdade (dígitos verificadores) é do servidor. */
export function maskCnpj(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 14);
  return digits
    .replace(/^(\d{2})(\d)/, "$1.$2")
    .replace(/^(\d{2})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/\.(\d{3})(\d)/, ".$1/$2")
    .replace(/(\d{4})(\d)/, "$1-$2");
}
