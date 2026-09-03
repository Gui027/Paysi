import { apiRequest, CursorPage } from "./api";
import { formatarCentavos } from "./moeda";

export type Bucket = "GUARANTEE" | "PENDING" | "RESERVE" | "AVAILABLE" | "DEBT" | "SYSTEM";
export type Direction = "CREDIT" | "DEBIT";

export type BalanceView = {
  guarantee: number;
  pending: number;
  reserve: number;
  available: number;
  debt: number;
  asOf: string;
};

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

export type LedgerItem = {
  entryId: number;
  bucket: Bucket;
  direction: Direction;
  amountCents: number;
  origin: string;
  reason: string | null;
  reference: string | null;
  availableAt: string | null;
  createdAt: string;
};

export function getBalance() {
  return apiRequest<BalanceView>("/v1/accounts/me/balance");
}

export function getKyc() {
  return apiRequest<KycView>("/v1/accounts/me");
}

export function getLedgerEntries(limit = 100) {
  return apiRequest<CursorPage<LedgerItem>>(`/v1/accounts/me/ledger?limit=${limit}`);
}

export type Recebivel = LedgerItem & { availableAt: string };

export function upcomingReceivables(items: LedgerItem[], now: Date = new Date()): Recebivel[] {
  return items
    .filter((item): item is Recebivel =>
      item.bucket === "PENDING" && item.direction === "CREDIT" && item.availableAt !== null && new Date(item.availableAt) > now)
    .sort((a, b) => new Date(a.availableAt).getTime() - new Date(b.availableAt).getTime())
    .slice(0, 5);
}

export async function fetchUpcomingReceivables(now: Date = new Date()): Promise<Recebivel[]> {
  const page = await getLedgerEntries(100);
  return upcomingReceivables(page.items, now);
}

export type DashboardAlert = {
  id: string;
  tone: "warning" | "danger";
  title: string;
  description: string;
  actionUrl: string | null;
};

const kycAlertCopy: Partial<Record<KycStatus, { title: string; description: string }>> = {
  PENDING: {
    title: "Verificação de identidade pendente",
    description: "Inicie a verificação para poder publicar ofertas e receber pagamentos.",
  },
  SUBMITTED: {
    title: "Verificação em análise",
    description: "Seus documentos estão em análise pelo provedor. Isso pode levar alguns dias.",
  },
  REJECTED: {
    title: "Verificação de identidade recusada",
    description: "Revise os requisitos pendentes e reenvie os documentos.",
  },
};

export function dashboardAlerts(balance: BalanceView, kyc: KycView): DashboardAlert[] {
  const alerts: DashboardAlert[] = [];

  const kycCopy = kycAlertCopy[kyc.kycStatus];
  if (kycCopy) {
    alerts.push({
      id: "kyc",
      tone: kyc.kycStatus === "REJECTED" ? "danger" : "warning",
      title: kycCopy.title,
      description: kycCopy.description,
      actionUrl: kyc.providerUrl,
    });
  }

  if (balance.debt !== 0) {
    alerts.push({
      id: "debt",
      tone: "danger",
      title: "Conta com débito em aberto",
      description: `Saldo de débito: ${formatarCentavos(balance.debt)}. O valor é descontado automaticamente das próximas vendas.`,
      actionUrl: null,
    });
  }

  return alerts;
}
