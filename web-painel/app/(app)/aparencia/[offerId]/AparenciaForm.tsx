"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  Appearance,
  AppearanceInput,
  AppearanceInputErrors,
  getAppearance,
  inputFromAppearance,
  updateAppearance,
  validateAppearanceInput,
} from "../../../../lib/aparencia";
import { ApiRequestError, fieldErrors } from "../../../../lib/api";
import { AssetKind, assetContentUrl, removeAsset, uploadAsset } from "../../../../lib/assets";
import { Botao, EmptyState, SeletorCor, Skeleton, Toast, UploadImagem } from "../../../../components/ui";

const blankAppearance: AppearanceInput = {
  logoAssetId: null,
  bannerAssetId: null,
  sideImageAssetId: null,
  primaryColor: "#2563EB",
  buttonText: "Comprar agora",
};

const uploadFieldByKind: Record<AssetKind, keyof AppearanceInput> = {
  LOGO: "logoAssetId",
  BANNER: "bannerAssetId",
  SIDE_IMAGE: "sideImageAssetId",
};

export function AparenciaForm({ offerId }: { offerId: string }) {
  const [values, setValues] = useState<AppearanceInput>(blankAppearance);
  const [baseline, setBaseline] = useState<AppearanceInput>(blankAppearance);
  const [errors, setErrors] = useState<AppearanceInputErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [uploading, setUploading] = useState<Record<AssetKind, boolean>>({
    LOGO: false,
    BANNER: false,
    SIDE_IMAGE: false,
  });

  useEffect(() => {
    let active = true;
    getAppearance(offerId).then((appearance: Appearance) => {
      if (!active) return;
      const input = inputFromAppearance(appearance);
      setValues(input);
      setBaseline(input);
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
      else setLoadFailed(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [offerId]);

  const dirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(baseline), [baseline, values]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty && !saving) { event.preventDefault(); event.returnValue = ""; } };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [dirty, saving]);

  function change<K extends keyof AppearanceInput>(field: K, value: AppearanceInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setGeneralError(null);
    setSaved(false);
  }

  async function handleUpload(kind: AssetKind, file: File) {
    setUploading(current => ({ ...current, [kind]: true }));
    setGeneralError(null);
    try {
      const asset = await uploadAsset(kind, file);
      change(uploadFieldByKind[kind], asset.id);
    } catch (error) {
      if (error instanceof ApiRequestError) setGeneralError(error.message);
      else setGeneralError("Não foi possível enviar a imagem. Tente novamente.");
    } finally {
      setUploading(current => ({ ...current, [kind]: false }));
    }
  }

  async function handleRemove(kind: AssetKind, assetId: string) {
    setGeneralError(null);
    try {
      await removeAsset(assetId);
      change(uploadFieldByKind[kind], null);
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        change(uploadFieldByKind[kind], null);
        return;
      }
      setGeneralError("Não foi possível remover a imagem. Tente novamente.");
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const validation = validateAppearanceInput(values);
    if (Object.keys(validation).length) {
      setErrors(validation);
      setGeneralError("Revise os campos destacados.");
      return;
    }

    setSaving(true);
    setGeneralError(null);
    try {
      const appearance = await updateAppearance(offerId, values);
      const input = inputFromAppearance(appearance);
      setValues(input);
      setBaseline(input);
      setSaved(true);
    } catch (error) {
      if (error instanceof ApiRequestError) {
        setErrors(fieldErrors(error.problem) as AppearanceInputErrors);
        setGeneralError(error.message);
      } else setGeneralError("Não foi possível salvar a aparência. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <Skeleton label="Carregando aparência do checkout" />;
  if (notFound) return <EmptyState title="Oferta não encontrada" description="A oferta não existe ou não está disponível para esta conta." />;
  if (loadFailed) return <Toast tone="danger">Não foi possível carregar a aparência desta oferta.</Toast>;

  return <>
    <header className="content-header"><div><h1>Aparência do checkout</h1><p>Personalize a identidade visual sem alterar o contrato de dados do checkout.</p></div></header>
    <form className="ui-card appearance-form" onSubmit={event => void submit(event)} noValidate>
      {generalError && <Toast tone="danger">{generalError}</Toast>}
      {saved && !generalError && <Toast tone="success">Aparência salva.</Toast>}
      <div className="appearance-grid">
        <UploadImagem label="Logo" hint="PNG ou JPEG, até 5 MB e 4096 px."
          error={errors.logoAssetId} uploading={uploading.LOGO}
          previewUrl={values.logoAssetId ? assetContentUrl(values.logoAssetId) : null}
          onSelect={file => void handleUpload("LOGO", file)}
          onRemove={values.logoAssetId ? () => void handleRemove("LOGO", values.logoAssetId!) : undefined} />
        <UploadImagem label="Banner" hint="PNG ou JPEG, até 5 MB e 4096 px."
          error={errors.bannerAssetId} uploading={uploading.BANNER}
          previewUrl={values.bannerAssetId ? assetContentUrl(values.bannerAssetId) : null}
          onSelect={file => void handleUpload("BANNER", file)}
          onRemove={values.bannerAssetId ? () => void handleRemove("BANNER", values.bannerAssetId!) : undefined} />
        <UploadImagem label="Imagem lateral" hint="PNG ou JPEG, até 5 MB e 4096 px."
          error={errors.sideImageAssetId} uploading={uploading.SIDE_IMAGE}
          previewUrl={values.sideImageAssetId ? assetContentUrl(values.sideImageAssetId) : null}
          onSelect={file => void handleUpload("SIDE_IMAGE", file)}
          onRemove={values.sideImageAssetId ? () => void handleRemove("SIDE_IMAGE", values.sideImageAssetId!) : undefined} />
      </div>
      <SeletorCor label="Cor primária" value={values.primaryColor} error={errors.primaryColor}
        onChange={value => change("primaryColor", value)} />
      <label className="ui-field" htmlFor="button-text"><span>Texto do botão</span>
        <input id="button-text" type="text" maxLength={40} value={values.buttonText}
          aria-invalid={Boolean(errors.buttonText)}
          onChange={event => change("buttonText", event.target.value)} />
        {errors.buttonText && <small className="ui-error">{errors.buttonText}</small>}
      </label>

      <div className="appearance-preview-grid">
        <Preview title="Prévia — desktop" className="appearance-preview-desktop" values={values} />
        <Preview title="Prévia — mobile" className="appearance-preview-mobile" values={values} />
      </div>

      <div className="ui-actions product-form-actions">
        <Botao type="submit" disabled={saving}>{saving ? "Salvando…" : "Salvar aparência"}</Botao>
        {dirty && <span className="unsaved-indicator" role="status">Alterações não salvas</span>}
      </div>
    </form>
  </>;
}

function Preview({ title, className, values }: { title: string; className: string; values: AppearanceInput }) {
  const color = /^#[0-9A-Fa-f]{6}$/.test(values.primaryColor) ? values.primaryColor : "#2563EB";
  return <div className={`appearance-preview ${className}`}>
    <header>{title}</header>
    <div className="appearance-preview-body">
      {values.bannerAssetId && <img src={assetContentUrl(values.bannerAssetId)} alt="" />}
      {values.logoAssetId && <img src={assetContentUrl(values.logoAssetId)} alt="" />}
      {values.sideImageAssetId && <img src={assetContentUrl(values.sideImageAssetId)} alt="" />}
      <button type="button" style={{ background: color }} disabled>{values.buttonText || "Comprar agora"}</button>
    </div>
  </div>;
}
