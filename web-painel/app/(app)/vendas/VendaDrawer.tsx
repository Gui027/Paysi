"use client";

import { useEffect, useId, useRef, useState } from "react";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import { parseMoneyToCents } from "../../../lib/ofertas";
import {
  getSale,
  payoutLabel,
  refundOriginLabel,
  refundSale,
  refundStatusLabel,
  SaleDetail,
  saleMethodLabel,
  saleStatusLabel,
  saleStatusTone,
  formatDocumento,
  formatTelefone,
  whatsappUrl,
} from "../../../lib/vendas";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const dateOnly = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });
const fmt = (value: string | null) => (value ? dateTime.format(new Date(value)) : "—");

type Tab = "venda" | "cliente" | "valores";

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return <div><dt>{rotulo}</dt><dd>{children}</dd></div>;
}

function Selo({ sale }: { sale: SaleDetail }) {
  return <span className={`pe-pill ${saleStatusTone[sale.status] === "success" ? "pe-pill-on" : saleStatusTone[sale.status] === "warning" ? "vd-pill-warn" : saleStatusTone[sale.status] === "danger" ? "af-pill-bad" : ""}`}>{saleStatusLabel[sale.status]}</span>;
}

/** Painel lateral "Ver detalhes" da venda: abas Venda, Cliente e Valores, e o reembolso. */
export function VendaDrawer({ saleId, onClose, onChanged }: { saleId: string | null; onClose: () => void; onChanged: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [sale, setSale] = useState<SaleDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("venda");
  const [menuOpen, setMenuOpen] = useState(false);
  const [refunding, setRefunding] = useState(false);
  const [mode, setMode] = useState<"total" | "partial">("total");
  const [amount, setAmount] = useState("");
  const [reason, setReason] = useState("");
  const [refundError, setRefundError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const idempotency = useRef("");

  async function load(id: string) {
    setLoading(true);
    setError(null);
    try {
      setSale(await getSale(id));
    } catch {
      setError("Não foi possível carregar os detalhes da venda.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (saleId) {
      setTab("venda");
      setSale(null);
      setNotice(null);
      setMenuOpen(false);
      setRefunding(false);
      void load(saleId);
      if (!dialog.open) dialog.showModal();
    } else if (dialog.open) dialog.close();
  }, [saleId]);

  function openRefund() {
    setMenuOpen(false);
    setMode("total");
    setAmount("");
    setReason("");
    setRefundError(null);
    idempotency.current = globalThis.crypto?.randomUUID?.() ?? `refund-${Date.now()}`;
    setRefunding(true);
  }

  async function confirmRefund() {
    if (!sale || submitting) return;
    let cents: number | null = null;
    if (mode === "partial") {
      cents = parseMoneyToCents(amount);
      if (cents === null || cents <= 0) {
        setRefundError("Informe o valor a reembolsar, com até duas casas decimais.");
        return;
      }
    }
    setSubmitting(true);
    setRefundError(null);
    try {
      await refundSale(sale.id, cents, reason, idempotency.current);
      setRefunding(false);
      setNotice(mode === "total" ? "Venda reembolsada." : "Reembolso parcial realizado.");
      await load(sale.id);
      onChanged();
    } catch (requestError) {
      setRefundError(requestError instanceof ApiRequestError ? requestError.message : "Não foi possível reembolsar. Tente novamente.");
    } finally {
      setSubmitting(false);
    }
  }

  const whatsapp = sale ? whatsappUrl(sale.buyer.phone) : null;

  return <dialog ref={dialogRef} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!submitting) onClose(); }}>
    <header className="vd-head">
      <h2 id={titleId}>Ver detalhes</h2>
      <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !submitting && onClose()}>✕</button>
    </header>

    {loading && !sale && <p className="vd-msg" role="status">Carregando…</p>}
    {error && <p className="vd-msg pe-error" role="alert">{error} <button type="button" className="pe-linkbtn" onClick={() => saleId && void load(saleId)}>Tentar novamente</button></p>}

    {sale && <>
      <div className="vd-tabbar">
        <div className="pe-tabs mk-tabs" role="tablist" aria-label="Detalhes da venda">
          {(["venda", "cliente", "valores"] as Tab[]).map(item => <button key={item} type="button" role="tab" id={`vd-tab-${item}`} aria-selected={tab === item} aria-controls="vd-painel" onClick={() => setTab(item)}>{item === "venda" ? "Venda" : item === "cliente" ? "Cliente" : "Valores"}</button>)}
        </div>
        <div className="vd-menu">
          <button type="button" className="vd-kebab" aria-label="Mais ações da venda" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen(open => !open)}>⋮</button>
          {menuOpen && <div className="prod-menu-list vd-menu-list" role="menu">
            <button type="button" role="menuitem" disabled={!sale.canRefund} onClick={openRefund}>Reembolsar venda</button>
            {!sale.canRefund && <small className="pe-hint vd-menu-hint">Só vendas pagas podem ser reembolsadas.</small>}
          </div>}
        </div>
      </div>

      {notice && <p className="vd-msg vd-ok" role="status">{notice}</p>}

      <div id="vd-painel" role="tabpanel" aria-labelledby={`vd-tab-${tab}`} className="mk-drawer-body">
        {tab === "venda" && <>
          <dl className="mk-facts">
            <Linha rotulo="ID da venda">{sale.code}</Linha>
            <Linha rotulo="Status"><Selo sale={sale} /></Linha>
            <Linha rotulo="Tipo">Sou produtor</Linha>
            <Linha rotulo="Valor líquido">{formatarCentavos(sale.amounts.netCents)}</Linha>
            <Linha rotulo="Produto">{sale.productName}</Linha>
            {sale.offerName && <Linha rotulo="Oferta">{sale.offerName}</Linha>}
            <Linha rotulo="Método de pagamento">{saleMethodLabel[sale.method]}</Linha>
            <Linha rotulo="Parcelas">{sale.installments}</Linha>
            {sale.cycleNumber !== null && <Linha rotulo="Assinatura">Ciclo {sale.cycleNumber}</Linha>}
            {sale.couponCode && <Linha rotulo="Cupom">{sale.couponCode}</Linha>}
            {sale.reference && <Linha rotulo="Referência do cliente">{sale.reference}</Linha>}
            <Linha rotulo="Data da criação">{fmt(sale.createdAt)}</Linha>
            {sale.approvedAt && <Linha rotulo="Data da aprovação">{fmt(sale.approvedAt)}</Linha>}
          </dl>
          {sale.refunds.length > 0 && <section aria-label="Reembolsos desta venda">
            <h3 className="vd-sub">Reembolsos</h3>
            <ul className="dash-list">{sale.refunds.map(refund => <li key={refund.id}>
              <span>{fmt(refund.createdAt)} · {refundOriginLabel[refund.requestedBy]} · {refundStatusLabel[refund.status]}{refund.reason ? ` · ${refund.reason}` : ""}</span>
              <strong>{formatarCentavos(refund.amountCents)}</strong>
            </li>)}</ul>
          </section>}
        </>}

        {tab === "cliente" && <dl className="mk-facts">
          <Linha rotulo="Nome">{sale.buyer.name}</Linha>
          <Linha rotulo="Email">{sale.buyer.email}</Linha>
          <Linha rotulo="Celular">{sale.buyer.phone ? <span className="vd-phone">{formatTelefone(sale.buyer.phone)}{whatsapp && <a className="vd-wa" href={whatsapp} target="_blank" rel="noopener noreferrer" aria-label="Conversar no WhatsApp">WhatsApp</a>}</span> : "Não informado"}</Linha>
          <Linha rotulo={sale.buyer.personType === "PJ" ? "CNPJ" : "CPF"}>{formatDocumento(sale.buyer.taxId)}</Linha>
          <Linha rotulo="IP">{sale.buyer.ip ?? "Não registrado"}</Linha>
        </dl>}

        {tab === "valores" && <>
          <dl className="mk-facts">
            <Linha rotulo="Preço base do produto">{formatarCentavos(sale.amounts.basePriceCents)}</Linha>
            {sale.amounts.discountCents > 0 && <Linha rotulo="Desconto (cupom)">{formatarCentavos(sale.amounts.discountCents)}</Linha>}
            <Linha rotulo="Valor pago pelo cliente">{formatarCentavos(sale.amounts.paidCents)}</Linha>
            <Linha rotulo="Taxas">{formatarCentavos(sale.amounts.feesCents)}</Linha>
            <Linha rotulo="Divisão dos valores"><ul className="vd-split">{sale.split.map(part => <li key={`${part.role}-${part.name}`}><strong>{part.name}</strong>: {formatarCentavos(part.amountCents)} <span className="pe-hint">({part.role === "SELLER" ? "produtor" : "afiliado"})</span></li>)}</ul></Linha>
            {sale.amounts.refundedCents > 0 && <Linha rotulo="Reembolsado">{formatarCentavos(sale.amounts.refundedCents)}</Linha>}
          </dl>
          <h3 className="vd-sub">Seu recebimento</h3>
          <dl className="mk-facts">
            <Linha rotulo="Status">{payoutLabel[sale.payoutState]}{sale.payoutState === "TO_RELEASE" && sale.availableAt ? ` em ${dateOnly.format(new Date(sale.availableAt))}` : ""}</Linha>
            <Linha rotulo="Valor">{formatarCentavos(sale.amounts.netCents)}</Linha>
          </dl>
        </>}
      </div>
    </>}

    {refunding && sale && <div className="vd-refund" role="group" aria-labelledby="vd-refund-title">
      <h3 id="vd-refund-title">Reembolsar venda {sale.code}</h3>
      <p className="pe-hint">Valor pago pelo cliente: {formatarCentavos(sale.amounts.paidCents)}. O reembolso devolve o dinheiro ao comprador e reverte o que você e o afiliado receberiam.</p>
      <fieldset className="pe-fieldset">
        <legend>Quanto reembolsar</legend>
        <label className="mk-terms"><input type="radio" name="refund-mode" checked={mode === "total"} onChange={() => setMode("total")} /> Valor total</label>
        <label className="mk-terms"><input type="radio" name="refund-mode" checked={mode === "partial"} onChange={() => setMode("partial")} /> Valor parcial</label>
      </fieldset>
      {mode === "partial" && <label className="pe-field"><span>Valor a reembolsar</span><span className="pe-money"><span aria-hidden="true">R$</span><input inputMode="decimal" placeholder="0,00" aria-label="Valor a reembolsar em reais" value={amount} onChange={event => setAmount(event.target.value)} /></span></label>}
      <label className="pe-field"><span>Motivo (opcional)</span><textarea rows={2} maxLength={200} value={reason} onChange={event => setReason(event.target.value)} /></label>
      {refundError && <p className="pe-error" role="alert">{refundError}</p>}
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-danger" disabled={submitting} onClick={() => void confirmRefund()}>{submitting ? "Reembolsando…" : "Confirmar reembolso"}</button>
        <button type="button" className="ui-button ui-button-secondary" disabled={submitting} onClick={() => setRefunding(false)}>Cancelar</button>
      </div>
    </div>}
  </dialog>;
}
