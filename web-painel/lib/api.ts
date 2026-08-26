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

export type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
};

export const SESSION_EXPIRED_EVENT = "paysi:session-expired";

export function fieldErrors(problem: ApiProblem): Record<string, string> {
  const entries = problem.fieldErrors ?? (problem.field && problem.message
    ? [{ field: problem.field, code: problem.code ?? "INVALID", message: problem.message }]
    : []);
  return Object.fromEntries(entries.map(({ field, message }) => [field, message]));
}

function correlationId() {
  return globalThis.crypto?.randomUUID?.() ?? `web-${Date.now()}`;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      "X-Correlation-Id": correlationId(),
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : null;

  if (!response.ok) {
    if (response.status === 401 && typeof window !== "undefined") {
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    throw new ApiRequestError(response.status, body ?? {});
  }

  return body as T;
}
