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

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: "include",
    headers: {
      Accept: "application/json",
      ...(init.body ? { "Content-Type": "application/json" } : {}),
      ...init.headers,
    },
  });

  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : null;

  if (!response.ok) {
    throw new ApiRequestError(response.status, body ?? {});
  }

  return body as T;
}
