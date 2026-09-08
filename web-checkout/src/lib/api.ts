export type FieldProblem = {
  field: string;
  code: string;
  message: string;
};

export type ApiProblem = {
  code?: string;
  message?: string;
  field?: string;
  fieldErrors?: FieldProblem[];
};

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ApiProblem,
  ) {
    super(problem.message ?? "Não foi possível concluir a solicitação.");
  }
}

const API_BASE_URL = (import.meta.env?.VITE_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");

export function fieldErrors(problem: ApiProblem): Record<string, string> {
  const entries = problem.fieldErrors ?? (problem.field && problem.message
    ? [{ field: problem.field, code: problem.code ?? "INVALID", message: problem.message }]
    : []);
  return Object.fromEntries(entries.map(({ field, message }) => [field, message]));
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : null;

  if (!response.ok) throw new ApiRequestError(response.status, body ?? {});
  return body as T;
}
