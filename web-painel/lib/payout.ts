import { ApiRequestError, apiRequest, fieldErrors } from "./api";

export type MfaOperation = "BANK_ACCOUNT_CHANGE" | "PAYOUT";

export type MfaChallenge = {
  challengeId: string;
  operation: MfaOperation;
  expiresAt: string;
  verified: boolean;
};

export type BankAccountInput = {
  holderType: "PF" | "PJ";
  holderTaxId: string;
  holderName: string;
  bankCode: string;
  branch: string;
  accountNumber: string;
  digit: string;
  accountType: "CHECKING" | "SAVINGS" | "PAYMENT";
  pixKeyType: "CPF" | "CNPJ" | "EMAIL" | "PHONE" | "EVP";
  pixKey: string;
};

export type BankAccount = {
  id: string;
  bankCode: string;
  branch: string;
  numberLast4: string;
  verifiedAt: string;
};

export type PayoutResult = {
  payoutId: string;
  status: string;
  receiptUrl: string | null;
  idempotentReplay: boolean;
};

export type BankAccountErrors = Partial<Record<keyof BankAccountInput, string>>;

export function onlyDigits(value: string, maximum?: number) {
  const digits = value.replace(/\D/g, "");
  return maximum ? digits.slice(0, maximum) : digits;
}
export function maskTaxId(value: string, holderType: BankAccountInput["holderType"]) {
  const digits = onlyDigits(value, holderType === "PF" ? 11 : 14);
  if (holderType === "PF") return digits.replace(/(\d{3})(\d)/, "$1.$2").replace(/(\d{3})(\d)/, "$1.$2").replace(/(\d{3})(\d{1,2})$/, "$1-$2");
  return digits.replace(/(\d{2})(\d)/, "$1.$2").replace(/(\d{3})(\d)/, "$1.$2").replace(/(\d{3})(\d)/, "$1/$2").replace(/(\d{4})(\d{1,2})$/, "$1-$2");
}

export function validateBankAccount(input: BankAccountInput): BankAccountErrors {
  const errors: BankAccountErrors = {};
  const expectedTaxLength = input.holderType === "PF" ? 11 : 14;
  if (onlyDigits(input.holderTaxId).length !== expectedTaxLength) errors.holderTaxId = `Informe um ${input.holderType === "PF" ? "CPF" : "CNPJ"} válido.`;
  if (input.holderName.trim().length < 3) errors.holderName = "Informe o nome completo do titular.";
  if (onlyDigits(input.bankCode).length !== 3) errors.bankCode = "Informe o código do banco com 3 dígitos.";
  if (!onlyDigits(input.branch)) errors.branch = "Informe a agência.";
  if (!onlyDigits(input.accountNumber)) errors.accountNumber = "Informe o número da conta.";
  if (!onlyDigits(input.digit)) errors.digit = "Informe o dígito.";
  if (!input.pixKey.trim()) errors.pixKey = "Informe a chave Pix.";
  return errors;
}

export function parsePayoutAmount(value: string) {
  const normalized = value.trim().replace(/\./g, "").replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  const [whole, fraction = ""] = normalized.split(".");
  const cents = Number(`${whole}${fraction.padEnd(2, "0")}`);
  return Number.isSafeInteger(cents) ? cents : null;
}

export function createMfaChallenge(operation: MfaOperation) {
  return apiRequest<MfaChallenge>("/v1/mfa/challenges", { method: "POST", body: JSON.stringify({ operation }) });
}

export function verifyMfaChallenge(challengeId: string, code: string) {
  return apiRequest<MfaChallenge>(`/v1/mfa/challenges/${encodeURIComponent(challengeId)}/verify`, { method: "POST", body: JSON.stringify({ code }) });
}

export function createBankAccount(input: BankAccountInput, challengeId: string) {
  return apiRequest<BankAccount>("/v1/accounts/me/bank-accounts", {
    method: "POST",
    headers: { "X-MFA-Challenge-Id": challengeId },
    body: JSON.stringify({ ...input, holderTaxId: onlyDigits(input.holderTaxId), bankCode: onlyDigits(input.bankCode), branch: onlyDigits(input.branch), accountNumber: onlyDigits(input.accountNumber), digit: onlyDigits(input.digit) }),
  });
}

export function requestPayout(amountCents: number, bankAccountId: string, challengeId: string, idempotencyKey: string) {
  return apiRequest<PayoutResult>("/v1/accounts/me/payouts", {
    method: "POST",
    headers: { "Idempotency-Key": idempotencyKey },
    body: JSON.stringify({ amountCents, bankAccountId, mfaChallengeId: challengeId }),
  });
}

export function payoutError(error: unknown) {
  if (!(error instanceof ApiRequestError)) return "Não foi possível concluir a operação. Tente novamente.";
  if (error.problem.code === "BANK_HOLDER_MISMATCH") return "A conta bancária precisa pertencer ao mesmo titular cadastrado no Paysi.";
  if (error.problem.code === "PAYOUT_BLOCKED_BY_DEBT") return "O saque está bloqueado enquanto houver saldo devedor.";
  if (error.problem.code === "MFA_CHALLENGE_INVALID") return "O código expirou. Solicite um novo desafio para continuar sem duplicar a operação.";
  return error.problem.message ?? "Não foi possível concluir a operação. Tente novamente.";
}

export function bankFieldErrors(error: unknown) {
  return error instanceof ApiRequestError ? fieldErrors(error.problem) as BankAccountErrors : {};
}
