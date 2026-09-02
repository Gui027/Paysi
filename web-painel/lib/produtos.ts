import { apiRequest, CursorPage } from "./api";

export type ProductStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "SUSPENDED";
export type ProductSegment = "SAAS" | "DIGITAL";
export type ProductChargeType = "ONE_TIME" | "SUBSCRIPTION";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  segment: ProductSegment;
  chargeType: ProductChargeType;
  affiliationEnabled: boolean;
  status: ProductStatus;
  createdAt: string;
};

export type ProductInput = {
  name: string;
  description: string | null;
  segment: ProductSegment;
  chargeType: ProductChargeType;
  affiliationEnabled: boolean;
};

export type ProductInputErrors = Partial<Record<keyof ProductInput, string>>;

export type ProductFilters = {
  query: string;
  status: "" | ProductStatus;
  segment: "" | ProductSegment;
  chargeType: "" | ProductChargeType;
};

export const productStatusLabel: Record<ProductStatus, string> = {
  DRAFT: "Rascunho",
  ACTIVE: "Publicado",
  PAUSED: "Pausado",
  SUSPENDED: "Suspenso",
};

export const productSegmentLabel: Record<ProductSegment, string> = {
  SAAS: "SaaS",
  DIGITAL: "Produto digital",
};

export const productChargeTypeLabel: Record<ProductChargeType, string> = {
  ONE_TIME: "Pagamento único",
  SUBSCRIPTION: "Assinatura",
};

export function productMatchesFilters(product: Product, filters: ProductFilters) {
  const normalizedQuery = filters.query.trim().toLocaleLowerCase("pt-BR");
  return (!normalizedQuery || product.name.toLocaleLowerCase("pt-BR").includes(normalizedQuery))
    && (!filters.status || product.status === filters.status)
    && (!filters.segment || product.segment === filters.segment)
    && (!filters.chargeType || product.chargeType === filters.chargeType);
}

export function validateProductInput(input: ProductInput): ProductInputErrors {
  const errors: ProductInputErrors = {};
  if (!input.name.trim()) errors.name = "Informe o nome do produto.";
  else if (input.name.trim().length > 120) errors.name = "Use no máximo 120 caracteres.";
  if ((input.description?.length ?? 0) > 2000) errors.description = "Use no máximo 2.000 caracteres.";
  return errors;
}

function normalizedInput(input: ProductInput): ProductInput {
  return {
    ...input,
    name: input.name.trim(),
    description: input.description?.trim() || null,
  };
}

export function listProducts(cursor?: string) {
  const query = new URLSearchParams({ limit: "20" });
  if (cursor) query.set("cursor", cursor);
  return apiRequest<CursorPage<Product>>(`/v1/products?${query}`);
}

export function getProduct(productId: string) {
  return apiRequest<Product>(`/v1/products/${encodeURIComponent(productId)}`);
}

export function createProduct(input: ProductInput) {
  return apiRequest<Product>("/v1/products", { method: "POST", body: JSON.stringify(normalizedInput(input)) });
}

export function updateProduct(productId: string, input: ProductInput) {
  return apiRequest<Product>(`/v1/products/${encodeURIComponent(productId)}`, { method: "PUT", body: JSON.stringify(normalizedInput(input)) });
}

export function archiveProduct(productId: string) {
  return apiRequest<void>(`/v1/products/${encodeURIComponent(productId)}`, { method: "DELETE" });
}
