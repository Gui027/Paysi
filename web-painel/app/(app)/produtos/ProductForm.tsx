"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { ApiRequestError, fieldErrors } from "../../../lib/api";
import { createProduct, getProduct, ProductInput, ProductInputErrors, updateProduct, validateProductInput } from "../../../lib/produtos";
import { Botao, Campo, Checkbox, EmptyState, Select, Skeleton, Toast } from "../../../components/ui";

const blankProduct: ProductInput = {
  name: "",
  description: null,
  segment: "DIGITAL",
  chargeType: "ONE_TIME",
  affiliationEnabled: false,
};

function inputFromProduct(product: Awaited<ReturnType<typeof getProduct>>): ProductInput {
  return {
    name: product.name,
    description: product.description,
    segment: product.segment,
    chargeType: product.chargeType,
    affiliationEnabled: product.affiliationEnabled,
  };
}

export function ProductForm({ mode, productId }: { mode: "create" | "edit"; productId?: string }) {
  const router = useRouter();
  const [values, setValues] = useState<ProductInput>(blankProduct);
  const [baseline, setBaseline] = useState<ProductInput>(blankProduct);
  const [errors, setErrors] = useState<ProductInputErrors>({});
  const [generalError, setGeneralError] = useState<string | null>(null);
  const [loading, setLoading] = useState(mode === "edit");
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [contractLocked, setContractLocked] = useState(false);

  useEffect(() => {
    if (mode !== "edit" || !productId) return;
    let active = true;
    getProduct(productId).then(product => {
      if (!active) return;
      const input = inputFromProduct(product);
      setValues(input);
      setBaseline(input);
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 404) setNotFound(true);
      else setLoadFailed(true);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [mode, productId]);

  const dirty = useMemo(() => JSON.stringify(values) !== JSON.stringify(baseline), [baseline, values]);
  useEffect(() => {
    const warn = (event: BeforeUnloadEvent) => { if (dirty && !saving) { event.preventDefault(); event.returnValue = ""; } };
    const guardLinks = (event: MouseEvent) => {
      if (!dirty || saving || !(event.target instanceof Element)) return;
      const link = event.target.closest("a[href]");
      if (link && !window.confirm("Descartar as alterações não salvas?")) {
        event.preventDefault();
        event.stopPropagation();
      }
    };
    window.addEventListener("beforeunload", warn);
    window.addEventListener("click", guardLinks, true);
    return () => {
      window.removeEventListener("beforeunload", warn);
      window.removeEventListener("click", guardLinks, true);
    };
  }, [dirty, saving]);

  function change<K extends keyof ProductInput>(field: K, value: ProductInput[K]) {
    setValues(current => ({ ...current, [field]: value }));
    setErrors(current => ({ ...current, [field]: undefined }));
    setGeneralError(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const validation = validateProductInput(values);
    if (Object.keys(validation).length) {
      setErrors(validation);
      setGeneralError("Revise os campos destacados.");
      return;
    }

    setSaving(true);
    setGeneralError(null);
    try {
      const product = mode === "create"
        ? await createProduct(values)
        : await updateProduct(productId!, values);
      setBaseline(inputFromProduct(product));
      router.push(`/produtos/${product.id}`);
      router.refresh();
    } catch (error) {
      if (error instanceof ApiRequestError) {
        if (error.status === 409 && error.problem.code === "PRODUCT_CONTRACT_IMMUTABLE") {
          setContractLocked(true);
          setValues(current => ({ ...current, segment: baseline.segment, chargeType: baseline.chargeType }));
          setGeneralError("Este produto já possui oferta. Segmento e cobrança foram restaurados e não podem mais ser alterados; os demais dados continuam preenchidos.");
        } else {
          setErrors(fieldErrors(error.problem) as ProductInputErrors);
          setGeneralError(error.message);
        }
      } else setGeneralError("Não foi possível salvar o produto. Tente novamente.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <Skeleton label="Carregando formulário do produto" />;
  if (notFound) return <EmptyState title="Produto não encontrado" description="O produto não existe ou não está disponível para esta conta." action={<Link className="ui-button ui-button-secondary" href="/produtos">Voltar aos produtos</Link>} />;
  if (loadFailed) return <Toast tone="danger">Não foi possível carregar os dados do produto. <Link href="/produtos">Voltar aos produtos</Link></Toast>;

  const cancelUrl = mode === "edit" && productId ? `/produtos/${productId}` : "/produtos";
  return <>
    <nav className="breadcrumb" aria-label="Navegação estrutural"><Link href="/produtos">Produtos</Link><span aria-hidden="true">/</span><span aria-current="page">{mode === "create" ? "Novo" : "Editar"}</span></nav>
    <header className="content-header"><div><h1>{mode === "create" ? "Novo produto" : "Editar produto"}</h1><p>Preencha os dados base e salve como rascunho.</p></div></header>
    <form className="ui-card product-form" onSubmit={event => void submit(event)} noValidate>
      {generalError && <Toast tone="danger">{generalError}</Toast>}
      <Campo label="Nome do produto" value={values.name} maxLength={120} error={errors.name} onChange={event => change("name", event.target.value)} required />
      <label className="ui-field"><span>Descrição</span><textarea value={values.description ?? ""} maxLength={2000} aria-invalid={Boolean(errors.description)} aria-describedby={errors.description ? "description-error" : "description-hint"} onChange={event => change("description", event.target.value)} /><small id={errors.description ? "description-error" : "description-hint"} className={errors.description ? "ui-error" : "ui-hint"}>{errors.description ?? `${values.description?.length ?? 0}/2.000 caracteres`}</small></label>
      <div className="product-form-grid">
        <div><Select label="Segmento" value={values.segment} disabled={contractLocked} error={errors.segment} onChange={event => change("segment", event.target.value as ProductInput["segment"])}><option value="DIGITAL">Produto digital</option><option value="SAAS">SaaS</option></Select><p className="field-explanation">Define a natureza do produto e algumas regras do checkout.</p></div>
        <div><Select label="Tipo de cobrança" value={values.chargeType} disabled={contractLocked} error={errors.chargeType} onChange={event => change("chargeType", event.target.value as ProductInput["chargeType"])}><option value="ONE_TIME">Pagamento único</option><option value="SUBSCRIPTION">Assinatura</option></Select><p className="field-explanation">Após criar uma oferta, segmento e cobrança tornam-se imutáveis.</p></div>
      </div>
      {contractLocked && <p className="contract-lock" role="status">Contrato protegido: uma oferta existente impede mudanças em segmento e cobrança.</p>}
      <Checkbox label="Permitir programa de afiliação para este produto" checked={values.affiliationEnabled} onChange={event => change("affiliationEnabled", event.target.checked)} />
      <p className="draft-note">Salvar este rascunho não inicia verificação de identidade (KYC) nem publica checkout.</p>
      <div className="ui-actions product-form-actions"><Botao type="submit" disabled={saving}>{saving ? "Salvando…" : "Salvar rascunho"}</Botao><Link className="ui-button ui-button-secondary" href={cancelUrl}>Cancelar</Link>{dirty && <span className="unsaved-indicator" role="status">Alterações não salvas</span>}</div>
    </form>
  </>;
}
