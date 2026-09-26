"use client";

import { useEffect, useId, useRef, useState } from "react";
import { ApiRequestError } from "../../../lib/api";
import { formatarCentavos } from "../../../lib/moeda";
import {
  cancelSubscription,
  cycleLabel,
  getSubscription,
  planName,
  statusText,
  SubscriptionDetail,
} from "../../../lib/assinaturas";
import { formatDocumento, formatTelefone, saleMethodLabel, saleStatusLabel, saleStatusTone, whatsappUrl } from "../../../lib/vendas";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const dateOnly = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });
const day = (value: string | null) => (value ? dateOnly.format(new Date(value)) : "—");

type Tab = "assinatura" | "cliente" | "pagamentos";

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return <div><dt>{rotulo}</dt><dd>{children}</dd></div>;
}

function pill(status: SubscriptionDetail["status"], pending: boolean) {
  if (pending) return "vd-pill-warn";
  return status === "ACTIVE" ? "pe-pill-on" : status === "CANCELED" ? "" : status === "PAST_DUE" ? "af-pill-bad" : "vd-pill-warn";
}

/** Painel "Ver detalhes" da assinatura: abas Assinatura, Cliente e Pagamentos, e o cancelamento. */
export function AssinaturaDrawer({ subscriptionId, onClose, onChanged }: { subscriptionId: string | null; onClose: () => void; onChanged: () => void }) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const [subscription, setSubscription] = useState<SubscriptionDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("assinatura");
  const [menuOpen, setMenuOpen] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  async function load(id: string) {
    setLoading(true);
    setError(null);
    try {
      setSubscription(await getSubscription(id));
    } catch {
      setError("Não foi possível carregar os detalhes da assinatura.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (subscriptionId) {
      setTab("assinatura");
      setSubscription(null);
      setNotice(null);
      setMenuOpen(false);
      setConfirming(false);
      void load(subscriptionId);
      if (!dialog.open) dialog.showModal();
    } else if (dialog.open) dialog.close();
  }, [subscriptionId]);

  async function confirmCancel() {
    if (!subscription || submitting) return;
    setSubmitting(true);
    setCancelError(null);
    try {
      await cancelSubscription(subscription.id);
      setConfirming(false);
      setNotice("Cancelamento agendado. O cliente mantém o acesso até o fim do período pago.");
      await load(subscription.id);
      onChanged();
    } catch (requestError) {
      setCancelError(requestError instanceof ApiRequestError ? requestError.message : "Não foi possível cancelar a assinatura. Tente novamente.");
    } finally {
      setSubmitting(false);
    }
  }

  const whatsapp = subscription ? whatsappUrl(subscription.buyer.phone) : null;

  return <dialog ref={dialogRef} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); if (!submitting) onClose(); }}>
    <header className="vd-head">
      <h2 id={titleId}>Ver detalhes</h2>
      <button type="button" className="cp-close" aria-label="Fechar" onClick={() => !submitting && onClose()}>✕</button>
    </header>

    {loading && !subscription && <p className="vd-msg" role="status">Carregando…</p>}
    {error && <p className="vd-msg pe-error" role="alert">{error} <button type="button" className="pe-linkbtn" onClick={() => subscriptionId && void load(subscriptionId)}>Tentar novamente</button></p>}

    {subscription && <>
      <div className="vd-tabbar">
        <div className="pe-tabs mk-tabs" role="tablist" aria-label="Detalhes da assinatura">
          {([["assinatura", "Assinatura"], ["cliente", "Cliente"], ["pagamentos", "Pagamentos"]] as [Tab, string][]).map(([id, label]) =>
            <button key={id} type="button" role="tab" id={`as-tab-${id}`} aria-selected={tab === id} aria-controls="as-painel" onClick={() => setTab(id)}>{label}</button>)}
        </div>
        <div className="vd-menu">
          <button type="button" className="vd-kebab" aria-label="Mais ações da assinatura" aria-haspopup="menu" aria-expanded={menuOpen} onClick={() => setMenuOpen(open => !open)}>⋮</button>
          {menuOpen && <div className="prod-menu-list vd-menu-list" role="menu">
            <button type="button" role="menuitem" disabled={!subscription.canCancel} onClick={() => { setMenuOpen(false); setCancelError(null); setConfirming(true); }}>Cancelar assinatura</button>
            {!subscription.canCancel && <small className="pe-hint vd-menu-hint">{subscription.status === "CANCELED" ? "Esta assinatura já foi cancelada." : "O cancelamento já está agendado."}</small>}
          </div>}
        </div>
      </div>

      {notice && <p className="vd-msg vd-ok" role="status">{notice}</p>}

      <div id="as-painel" role="tabpanel" aria-labelledby={`as-tab-${tab}`} className="mk-drawer-body">
        {tab === "assinatura" && <dl className="mk-facts">
          <Linha rotulo="Data de início">{dateTime.format(new Date(subscription.createdAt))}</Linha>
          <Linha rotulo="Status"><span className={`pe-pill ${pill(subscription.status, subscription.cancelPending)}`}>{statusText(subscription)}</span></Linha>
          <Linha rotulo="Acesso liberado até">{day(subscription.accessUntil)}</Linha>
          {subscription.status === "TRIAL" && <Linha rotulo="Teste grátis até">{day(subscription.trialEndsAt)}</Linha>}
          <Linha rotulo="Tipo">Sou produtor</Linha>
          <Linha rotulo="Produto">{subscription.productName}</Linha>
          <Linha rotulo="Plano">{planName(subscription)}</Linha>
          <Linha rotulo="Valor líquido">{subscription.netCents === null ? "—" : formatarCentavos(subscription.netCents)}</Linha>
          <Linha rotulo="Parcelas">{subscription.installments}</Linha>
          <Linha rotulo="Frequência">{cycleLabel[subscription.cycle]}</Linha>
          <Linha rotulo="Cobranças aprovadas">{subscription.approvedCharges}</Linha>
          <Linha rotulo="Método de pagamento">{saleMethodLabel[subscription.method]}</Linha>
          <Linha rotulo="Próxima cobrança">{subscription.status === "CANCELED" ? "—" : day(subscription.nextChargeAt)}</Linha>
          {subscription.canceledAt && <Linha rotulo={subscription.status === "CANCELED" ? "Cancelada em" : "Cancelamento pedido em"}>{day(subscription.canceledAt)}</Linha>}
        </dl>}

        {tab === "cliente" && <dl className="mk-facts">
          <Linha rotulo="Nome">{subscription.buyer.name}</Linha>
          <Linha rotulo="Email">{subscription.buyer.email}</Linha>
          <Linha rotulo="Celular">{subscription.buyer.phone ? <span className="vd-phone">{formatTelefone(subscription.buyer.phone)}{whatsapp && <a className="vd-wa" href={whatsapp} target="_blank" rel="noopener noreferrer" aria-label="Conversar no WhatsApp">WhatsApp</a>}</span> : "Não informado"}</Linha>
          <Linha rotulo={subscription.buyer.personType === "PJ" ? "CNPJ" : "CPF"}>{formatDocumento(subscription.buyer.taxId)}</Linha>
          <Linha rotulo="IP">{subscription.buyer.ip ?? "Não registrado"}</Linha>
        </dl>}

        {tab === "pagamentos" && (subscription.payments.length === 0 ? <p className="pe-empty">Ainda não houve cobranças. {subscription.status === "TRIAL" ? "A primeira acontece quando o teste grátis terminar." : ""}</p> :
          <table className="prod-table">
            <thead><tr><th scope="col">Data</th><th scope="col">Status</th><th scope="col">Valor líquido</th></tr></thead>
            <tbody>{subscription.payments.map(payment => <tr key={payment.chargeId}>
              <td className="prod-muted">{dateTime.format(new Date(payment.paidAt ?? payment.createdAt))}<small className="vd-offer">Ciclo {payment.cycleNumber}</small></td>
              <td><span className={`pe-pill ${saleStatusTone[payment.status] === "success" ? "pe-pill-on" : saleStatusTone[payment.status] === "danger" ? "af-pill-bad" : saleStatusTone[payment.status] === "warning" ? "vd-pill-warn" : ""}`}>{saleStatusLabel[payment.status]}</span></td>
              <td>{formatarCentavos(payment.netCents)}</td>
            </tr>)}</tbody>
          </table>)}
      </div>
    </>}

    {confirming && subscription && <div className="vd-refund" role="group" aria-labelledby="as-cancel-title">
      <h3 id="as-cancel-title">Cancelar a assinatura de {subscription.buyer.name}?</h3>
      <p className="pe-hint">Não haverá novas cobranças. O cliente mantém o acesso até {day(subscription.accessUntil)} (fim do período já pago) e depois a assinatura é encerrada.</p>
      {cancelError && <p className="pe-error" role="alert">{cancelError}</p>}
      <div className="ui-actions">
        <button type="button" className="ui-button ui-button-danger" disabled={submitting} onClick={() => void confirmCancel()}>{submitting ? "Cancelando…" : "Confirmar cancelamento"}</button>
        <button type="button" className="ui-button ui-button-secondary" disabled={submitting} onClick={() => setConfirming(false)}>Voltar</button>
      </div>
    </div>}
  </dialog>;
}
