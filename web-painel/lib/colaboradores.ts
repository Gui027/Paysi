import { apiRequest } from "./api";

export type CollaboratorArea = "products" | "sales" | "subscriptions" | "finance" | "reports" | "affiliates" | "integrations";
export type Collaborator = { id: string; email: string; status: "PENDING" | "ACTIVE"; permissions: string[]; invitedAt: string };
export type CollaboratorsPage = { items: Collaborator[]; page: number; size: number; total: number; totalPages: number };

export const areaLabels: Record<CollaboratorArea, string> = {
  products: "Produtos", sales: "Vendas", subscriptions: "Assinaturas", finance: "Financeiro",
  reports: "Relatórios", affiliates: "Afiliados", integrations: "Integrações",
};
export const areaOrder = Object.keys(areaLabels) as CollaboratorArea[];
export const statusLabel: Record<Collaborator["status"], string> = { PENDING: "Pendente", ACTIVE: "Ativo" };

export function listCollaborators(q: string, page: number) {
  const params = new URLSearchParams({ page: String(page) });
  if (q.trim()) params.set("q", q.trim());
  return apiRequest<CollaboratorsPage>(`/v1/collaborators?${params}`);
}

/** "Acesso total" vira ALL; senão vai a lista das áreas marcadas. */
export function permissionsPayload(fullAccess: boolean, areas: CollaboratorArea[]): string[] {
  return fullAccess ? ["ALL"] : areas;
}

export function hasFullAccess(collaborator: Pick<Collaborator, "permissions">): boolean {
  return collaborator.permissions.includes("ALL");
}

export function isValidEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim());
}

export function addCollaborator(email: string, permissions: string[]) {
  return apiRequest<Collaborator>("/v1/collaborators", { method: "POST", body: JSON.stringify({ email: email.trim(), permissions }) });
}

export function updateCollaborator(id: string, permissions: string[]) {
  return apiRequest<Collaborator>(`/v1/collaborators/${id}`, { method: "PUT", body: JSON.stringify({ permissions }) });
}

export function resendInvite(id: string) {
  return apiRequest<void>(`/v1/collaborators/${id}/resend`, { method: "POST" });
}

export function removeCollaborator(id: string) {
  return apiRequest<void>(`/v1/collaborators/${id}`, { method: "DELETE" });
}
