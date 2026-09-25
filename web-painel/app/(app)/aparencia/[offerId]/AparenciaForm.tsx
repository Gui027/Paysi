"use client";

import Link from "next/link";
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
import { formatOfferMoney, getOffer } from "../../../../lib/ofertas";
import { getProduct } from "../../../../lib/produtos";
import { EmptyState, SeletorCor, Skeleton, Toast, UploadImagem } from "../../../../components/ui";

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
  const [device, setDevice] = useState<"desktop" | "mobile">("desktop");
  const [productId, setProductId] = useState<string | null>(null);
  const [productName, setProductName] = useState("");
  const [priceLabel, setPriceLabel] = useState("");
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

  // Nome e preço só ilustram a prévia; se falhar, a prévia usa textos genéricos.
  useEffect(() => {
    let active = true;
    getOffer(offerId).then(offer => {
      if (!active) return;
      setProductId(offer.productId);
      setPriceLabel(formatOfferMoney(offer.priceCents));
      return getProduct(offer.productId).then(product => { if (active) setProductName(product.name); });
    }).catch(() => undefined);
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

  const color = /^#[0-9A-Fa-f]{6}$/.test(values.primaryColor) ? values.primaryColor : "#1D6BD8";
  const imageField = (kind: AssetKind, label: string, field: keyof AppearanceInput) => <UploadImagem label={label} hint="PNG ou JPEG, até 5 MB e 4096 px."
    error={errors[field]} uploading={uploading[kind]}
    previewUrl={values[field] ? assetContentUrl(values[field]!) : null}
    onSelect={file => void handleUpload(kind, file)}
    onRemove={values[field] ? () => void handleRemove(kind, values[field]!) : undefined} />;

  return <form className="ck" onSubmit={event => void submit(event)} noValidate>
    <header className="ck-bar">
      <Link href={productId ? `/produtos/${productId}?aba=checkout` : "/produtos"} className="pe-back" aria-label="Voltar ao produto">←</Link>
      <strong className="ck-name">Personalizar checkout</strong>
      <div className="ck-device" role="group" aria-label="Tamanho da tela">
        <button type="button" aria-pressed={device === "desktop"} onClick={() => setDevice("desktop")}>Desktop</button>
        <button type="button" aria-pressed={device === "mobile"} onClick={() => setDevice("mobile")}>Celular</button>
      </div>
      <span className="ck-state" role="status">{dirty ? "Existem alterações não salvas" : saved ? "Tudo certo!" : ""}</span>
      <button type="submit" className="ui-button ui-button-primary" disabled={saving}>{saving ? "Salvando…" : "Salvar checkout"}</button>
    </header>

    <div className="ck-body">
      <section className="ck-stage" aria-label="Prévia do checkout">
        <div className={`ck-frame ck-frame-${device}`}>
          <div className="ck-page" style={{ ["--ck-color" as string]: color }}>
            {values.bannerAssetId && <img className="ck-banner" src={assetContentUrl(values.bannerAssetId)} alt="" />}
            <div className="ck-head">
              {values.logoAssetId ? <img className="ck-logo" src={assetContentUrl(values.logoAssetId)} alt="" /> : <span className="ck-logo ck-logo-empty" aria-hidden="true" />}
              <strong>{productName || "Nome do produto"}</strong>
            </div>
            <div className="ck-columns">
              <div className="ck-card">
                <span className="ck-line" /><span className="ck-line" /><span className="ck-line ck-short" />
                {priceLabel && <p className="ck-price">{priceLabel}</p>}
                <button type="button" className="ck-pay" disabled>{values.buttonText || "Comprar agora"}</button>
              </div>
              {values.sideImageAssetId && <img className="ck-side" src={assetContentUrl(values.sideImageAssetId)} alt="" />}
            </div>
          </div>
        </div>
      </section>

      <aside className="ck-panel" aria-label="Configurações do checkout">
        {generalError && <Toast tone="danger">{generalError}</Toast>}
        <h2>Aparência</h2>
        {imageField("LOGO", "Logo", "logoAssetId")}
        {imageField("BANNER", "Banner", "bannerAssetId")}
        {imageField("SIDE_IMAGE", "Imagem lateral", "sideImageAssetId")}
        <SeletorCor label="Cor primária" value={values.primaryColor} error={errors.primaryColor} onChange={value => change("primaryColor", value)} />
        <label className="ui-field" htmlFor="button-text"><span>Texto do botão</span>
          <input id="button-text" type="text" maxLength={40} value={values.buttonText} aria-invalid={Boolean(errors.buttonText)} onChange={event => change("buttonText", event.target.value)} />
          {errors.buttonText && <small className="ui-error">{errors.buttonText}</small>}
        </label>
      </aside>
    </div>
  </form>;
}
