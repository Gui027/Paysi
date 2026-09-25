"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useId, useRef, useState } from "react";
import { ApiRequestError } from "../../../lib/api";
import { BillingCycle, createOffer, parseMoneyToCents, validateOfferInput } from "../../../lib/ofertas";
import { createProduct, ProductChargeType, ProductSegment } from "../../../lib/produtos";

const cycleLabel: Record<BillingCycle, string> = { MONTHLY: "Mensal", QUARTERLY: "Trimestral", SEMIANNUAL: "Semestral", ANNUAL: "Anual" };

// Criação em dois passos, como no Kiwify: primeiro o tipo, depois nome, descrição e preço.
// O produto nasce como rascunho com uma oferta padrão; o resto se ajusta na página do produto.
export function CriarProdutoModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [step, setStep] = useState<1 | 2>(1);
  const [chargeType, setChargeType] = useState<ProductChargeType>("ONE_TIME");
  const [segment, setSegment] = useState<ProductSegment>("DIGITAL");
  const [cycle, setCycle] = useState<BillingCycle>("MONTHLY");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [price, setPrice] = useState("");
  const [errors, setErrors] = useState<{ name?: string; price?: string }>({});
  const [failure, setFailure] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) { setStep(1); setErrors({}); setFailure(null); dialog.showModal(); }
    if (!open && dialog.open) dialog.close();
  }, [open]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const cents = parseMoneyToCents(price);
    const next: { name?: string; price?: string } = {};
    if (!name.trim()) next.name = "Informe o nome do produto.";
    const offerInput = {
      priceCents: cents ?? 0, cycle: chargeType === "SUBSCRIPTION" ? cycle : null, trialDays: 0, trialRequiresCard: true,
      guaranteeDays: 7, maxInstallments: 1, boletoDueDays: 3, boletoAdvanceDays: 5,
      paymentMethods: segment === "SAAS" ? ["PIX", "CARD", "BOLETO"] as ("PIX" | "CARD" | "BOLETO")[] : ["PIX", "CARD"] as ("PIX" | "CARD")[],
      payoutDelay: "D32" as const,
    };
    const offerErrors = validateOfferInput(offerInput, { segment, chargeType });
    if (cents === null || offerErrors.price) next.price = offerErrors.price ?? "Informe um preço válido.";
    setErrors(next);
    if (Object.keys(next).length) return;

    setSaving(true);
    setFailure(null);
    try {
      const product = await createProduct({ name, description: description || null, segment, chargeType, affiliationEnabled: false });
      try { await createOffer(product.id, offerInput); } catch { /* o produto já existe; a oferta pode ser criada na página dele */ }
      router.push(`/produtos/${product.id}`);
    } catch (error) {
      setFailure(error instanceof ApiRequestError ? error.message : "Não foi possível criar o produto. Tente novamente.");
      setSaving(false);
    }
  }

  return <dialog ref={dialogRef} className="cp-dialog" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!saving) onClose(); }}>
    <header className="cp-head"><h2 id={titleId}>Criar produto</h2>
      <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !saving && onClose()}>✕</button></header>
    {step === 1 ? <div className="cp-body">
      <label className="cp-field"><span>Tipo de pagamento</span>
        <select value={chargeType} onChange={event => setChargeType(event.target.value as ProductChargeType)}>
          <option value="ONE_TIME">Pagamento único</option><option value="SUBSCRIPTION">Assinatura recorrente</option>
        </select></label>
      <label className="cp-field"><span>Tipo de produto</span>
        <select value={segment} onChange={event => setSegment(event.target.value as ProductSegment)}>
          <option value="DIGITAL">Produto digital</option><option value="SAAS">Software (SaaS)</option>
        </select></label>
      <button type="button" className="cp-primary" onClick={() => setStep(2)}>Continuar →</button>
    </div> : <form className="cp-body" onSubmit={event => void submit(event)} noValidate>
      <button type="button" className="cp-back" onClick={() => setStep(1)}>← Voltar</button>
      {failure && <p className="cp-error" role="alert">{failure}</p>}
      <label className="cp-field"><span>Nome do produto</span>
        <input value={name} maxLength={120} aria-invalid={Boolean(errors.name)} onChange={event => setName(event.target.value)} />
        {errors.name && <small className="cp-error">{errors.name}</small>}</label>
      <label className="cp-field"><span>Descrição</span>
        <textarea value={description} maxLength={2000} rows={3} placeholder="Explique o seu produto" onChange={event => setDescription(event.target.value)} /></label>
      {chargeType === "SUBSCRIPTION" && <label className="cp-field"><span>Cobrança</span>
        <select value={cycle} onChange={event => setCycle(event.target.value as BillingCycle)}>
          {Object.entries(cycleLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select></label>}
      <label className="cp-field"><span>Preço</span>
        <span className="cp-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" value={price} aria-label="Preço em reais" aria-invalid={Boolean(errors.price)} onChange={event => setPrice(event.target.value)} /></span>
        {errors.price && <small className="cp-error">{errors.price}</small>}</label>
      <button type="submit" className="cp-primary" disabled={saving}>{saving ? "Criando…" : "Criar produto"}</button>
    </form>}
  </dialog>;
}
