import { apiRequest } from "./api";

export type Appearance = {
  logoAssetId: string | null;
  bannerAssetId: string | null;
  sideImageAssetId: string | null;
  primaryColor: string;
  buttonText: string;
  updatedAt: string;
};

export type AppearanceInput = {
  logoAssetId: string | null;
  bannerAssetId: string | null;
  sideImageAssetId: string | null;
  primaryColor: string;
  buttonText: string;
};

export type AppearanceInputErrors = Partial<Record<keyof AppearanceInput, string>>;

const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

export function validateAppearanceInput(input: AppearanceInput): AppearanceInputErrors {
  const errors: AppearanceInputErrors = {};
  if (!COLOR_PATTERN.test(input.primaryColor)) {
    errors.primaryColor = "Use uma cor hexadecimal no formato #RRGGBB.";
  }
  const buttonText = input.buttonText.trim();
  if (!buttonText) errors.buttonText = "Informe o texto do botão.";
  else if (buttonText.length > 40) errors.buttonText = "Use no máximo 40 caracteres.";
  return errors;
}

export function inputFromAppearance(appearance: Appearance): AppearanceInput {
  return {
    logoAssetId: appearance.logoAssetId,
    bannerAssetId: appearance.bannerAssetId,
    sideImageAssetId: appearance.sideImageAssetId,
    primaryColor: appearance.primaryColor,
    buttonText: appearance.buttonText,
  };
}

export function getAppearance(offerId: string) {
  return apiRequest<Appearance>(`/v1/offers/${encodeURIComponent(offerId)}/appearance`);
}

export function updateAppearance(offerId: string, input: AppearanceInput) {
  return apiRequest<Appearance>(`/v1/offers/${encodeURIComponent(offerId)}/appearance`, {
    method: "PUT",
    body: JSON.stringify({ ...input, buttonText: input.buttonText.trim() }),
  });
}
