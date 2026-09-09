import { apiRequest, ApiProblem, ApiRequestError } from "./api";

export type AssetKind = "LOGO" | "BANNER" | "SIDE_IMAGE";

export type Asset = {
  id: string;
  kind: AssetKind;
  contentType: string;
  byteSize: number;
  width: number;
  height: number;
  url: string;
};

export function assetContentUrl(assetId: string) {
  return `/api/v1/assets/${encodeURIComponent(assetId)}/content`;
}

export async function uploadAsset(kind: AssetKind, file: File): Promise<Asset> {
  const body = new FormData();
  body.append("kind", kind);
  body.append("file", file);
  const response = await fetch("/api/v1/assets", {
    method: "POST",
    credentials: "include",
    headers: { Accept: "application/json" },
    body,
  });
  const contentType = response.headers.get("content-type") ?? "";
  const parsed = contentType.includes("application/json") ? await response.json() : null;
  if (!response.ok) throw new ApiRequestError(response.status, (parsed ?? {}) as ApiProblem);
  return parsed as Asset;
}

export function removeAsset(assetId: string) {
  return apiRequest<void>(`/v1/assets/${encodeURIComponent(assetId)}`, { method: "DELETE" });
}
