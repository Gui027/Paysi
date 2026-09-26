import { apiRequest, ApiRequestError } from "./api";
import { formatarCentavos } from "./moeda";

export type ReportColumnType = "text" | "int" | "money" | "date" | "datetime";
export type ReportColumn = { key: string; label: string; type: ReportColumnType };
export type ReportCell = string | number;
export type ReportChart = {
  points: { label: string; valueCents: number; heightPercent: string; secondCents: number; secondPercent: string }[];
  ticks: { cents: number; bottomPercent: string }[];
};
export type Report = {
  id: string;
  title: string;
  columns: ReportColumn[];
  rows: ReportCell[][];
  chart: ReportChart;
  totals: Record<string, number>;
  page: number;
  size: number;
  total: number;
  totalPages: number;
};
export type ReportQuery = { from: string; to: string; productId: string; q: string; tab: string; page: number };
export type ExportFormat = "xlsx" | "csv";

export const emptyReportQuery: ReportQuery = { from: "", to: "", productId: "", q: "", tab: "", page: 1 };

export type HubItem = { id: string; label: string };
export const hubItems: readonly HubItem[] = [
  { id: "co-producao-recebida", label: "Receita de co-produção" },
  { id: "produto", label: "Receita por produto" },
  { id: "abandonadas", label: "Vendas abandonadas" },
  { id: "alunos", label: "Engajamento dos alunos" },
  { id: "afiliado", label: "Receita por afiliado" },
  { id: "saldo-receber", label: "Saldo a receber" },
  { id: "recebiveis-cartao", label: "Recebíveis de cartão" },
  { id: "assinaturas-canceladas", label: "Assinaturas canceladas" },
  { id: "agente-recuperador", label: "Agente recuperador de vendas" },
];

export function reportParams(query: ReportQuery, withPage: boolean): string {
  const params = new URLSearchParams();
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (query.productId) params.set("productId", query.productId);
  if (query.q.trim()) params.set("q", query.q.trim());
  if (query.tab) params.set("tab", query.tab);
  if (withPage) params.set("page", String(query.page));
  return params.toString();
}

export function getReport(id: string, query: ReportQuery): Promise<Report> {
  return apiRequest<Report>(`/v1/reports/${id}?${reportParams(query, true)}`);
}

export async function downloadReport(id: string, format: ExportFormat, query: ReportQuery): Promise<Blob> {
  const params = new URLSearchParams(reportParams(query, false));
  params.set("format", format);
  const response = await fetch(`/api/v1/reports/${id}/export?${params}`, { credentials: "include" });
  if (!response.ok) {
    const body = response.headers.get("content-type")?.includes("json") ? await response.json() : {};
    throw new ApiRequestError(response.status, body);
  }
  return response.blob();
}

// ---------- formatação (só texto) ----------

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

export function formatDay(iso: string): string {
  return /^\d{4}-\d{2}-\d{2}$/.test(iso) ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : iso;
}

export function formatCell(column: ReportColumn, value: ReportCell): string {
  if (value === "" || value === null || value === undefined) return "";
  switch (column.type) {
    case "money": return formatarCentavos(Number(value));
    case "date": return formatDay(String(value));
    case "datetime": return dateTime.format(new Date(String(value)));
    default: return String(value);
  }
}

/** Link do WhatsApp com a mensagem pronta; sem telefone válido não há o que abrir. */
export function whatsappLink(phone: string, buyerName: string, productName: string): string | null {
  const digits = phone.replace(/\D/g, "");
  if (digits.length < 10) return null;
  const number = digits.length <= 11 ? `55${digits}` : digits;
  const first = buyerName.trim().split(/\s+/)[0] ?? "";
  const text = `Olá${first ? `, ${first}` : ""}! Vi que você começou a compra de "${productName}" e não finalizou. Posso te ajudar?`;
  return `https://wa.me/${number}?text=${encodeURIComponent(text)}`;
}

// ---------- períodos ----------

export type PeriodPreset = "today" | "7d" | "30d" | "all" | "next7" | "next30" | "next90" | "custom";
export const pastPresets: readonly PeriodPreset[] = ["today", "7d", "30d", "all"];
export const futurePresets: readonly PeriodPreset[] = ["next7", "next30", "next90", "all"];

export const presetLabel: Record<PeriodPreset, string> = {
  today: "Hoje", "7d": "Últimos 7 dias", "30d": "Últimos 30 dias", all: "Tempo todo",
  next7: "Próximos 7 dias", next30: "Próximos 30 dias", next90: "Próximos 90 dias", custom: "Período",
};

export function toIso(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

function shifted(base: Date, days: number): Date {
  const copy = new Date(base.getFullYear(), base.getMonth(), base.getDate());
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function presetRange(preset: PeriodPreset, today: Date): { from: string; to: string } {
  switch (preset) {
    case "today": return { from: toIso(today), to: toIso(today) };
    case "7d": return { from: toIso(shifted(today, -6)), to: toIso(today) };
    case "30d": return { from: toIso(shifted(today, -29)), to: toIso(today) };
    case "next7": return { from: toIso(today), to: toIso(shifted(today, 7)) };
    case "next30": return { from: toIso(today), to: toIso(shifted(today, 30)) };
    case "next90": return { from: toIso(today), to: toIso(shifted(today, 90)) };
    default: return { from: "", to: "" };
  }
}

export const monthNames = ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"];

/** Semanas (segunda a domingo) de um mês; dias fora do mês vêm como null. */
export function monthGrid(year: number, month: number): (string | null)[][] {
  const first = new Date(year, month, 1);
  const offset = (first.getDay() + 6) % 7;
  const days = new Date(year, month + 1, 0).getDate();
  const cells: (string | null)[] = Array.from({ length: offset }, () => null);
  for (let day = 1; day <= days; day += 1) cells.push(toIso(new Date(year, month, day)));
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks: (string | null)[][] = [];
  for (let index = 0; index < cells.length; index += 7) weeks.push(cells.slice(index, index + 7));
  return weeks;
}
