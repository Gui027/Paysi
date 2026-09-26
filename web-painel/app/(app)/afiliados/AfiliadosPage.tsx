"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Dialog, EmptyState, Skeleton, Toast } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/api";
import {
  Affiliation,
  AffiliationRecurrence,
  AffiliationStatus,
  affiliationStatusLabel,
  approveAffiliation,
  endAffiliation,
  endReasonLabel,
  formatCommissionBps,
  getAffiliateProgram,
  listSellerAffiliations,
  parseCommissionPercent,
  recurrenceLabel,
} from "../../../lib/afiliados";

type Aba = "ativos" | "pendentes" | "inativos";
const abas: readonly [Aba, string][] = [["ativos", "Ativos"], ["pendentes", "Solicitações pendentes"], ["inativos", "Recusados, bloqueados ou cancelados"]];
const statusDaAba: Record<Aba, AffiliationStatus[]> = { ativos: ["APPROVED"], pendentes: ["PENDING"], inativos: ["ENDED", "FRAUD_ENDED"] };
const emptyText: Record<Aba, string> = {
  ativos: "Nenhum afiliado ativo ainda. Compartilhe o link de convite do produto ou aguarde pedidos pelo marketplace.",
  pendentes: "Nenhuma solicitação pendente no momento.",
  inativos: "Nenhum vínculo encerrado.",
};

type EndAction = { targets: Affiliation[]; reason: "BY_SELLER" | "FRAUD"; rejection: boolean };

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(new Date(value)) : "—";
}

function actionError(error: unknown) {
  if (error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) {
    return "A ação não está disponível para esta conta ou o vínculo já mudou.";
  }
  if (error instanceof ApiRequestError && error.status === 409) return "O vínculo já foi atualizado. Recarregue a lista.";
  return "Não foi possível concluir a ação. Tente novamente.";
}

function statusText(item: Affiliation) {
  if (item.status === "ENDED") return item.endedReason === "BY_AFFILIATE" ? "Cancelado pelo afiliado" : item.approvedAt ? "Encerrado" : "Recusado";
  return affiliationStatusLabel[item.status];
}

export function AfiliadosPage() {
  const [affiliations, setAffiliations] = useState<Affiliation[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [aba, setAba] = useState<Aba>("ativos");
  const [query, setQuery] = useState("");
  const [productId, setProductId] = useState("");
  const [checked, setChecked] = useState<Set<string>>(() => new Set());
  const [menuOpen, setMenuOpen] = useState(false);
  const [detail, setDetail] = useState<Affiliation | null>(null);
  const [approval, setApproval] = useState<Affiliation[] | null>(null);
  const [commission, setCommission] = useState("30");
  const [recurrence, setRecurrence] = useState<AffiliationRecurrence>("FIRST_CHARGE");
  const [commissionError, setCommissionError] = useState<string | undefined>();
  const [endAction, setEndAction] = useState<EndAction | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listSellerAffiliations();
      setAffiliations(page.items);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar os afiliados. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => { setChecked(new Set()); setMenuOpen(false); }, [aba, query, productId]);

  const products = useMemo(() => {
    const map = new Map<string, string>();
    affiliations.forEach(item => map.set(item.productId, item.product));
    return [...map.entries()];
  }, [affiliations]);

  const counts = useMemo(() => Object.fromEntries(abas.map(([id]) => [id, affiliations.filter(item => statusDaAba[id].includes(item.status)).length])) as Record<Aba, number>, [affiliations]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    return affiliations.filter(item => statusDaAba[aba].includes(item.status)
      && (!productId || item.productId === productId)
      && (!normalized || item.product.toLocaleLowerCase("pt-BR").includes(normalized)
        || item.affiliate.toLocaleLowerCase("pt-BR").includes(normalized)));
  }, [affiliations, aba, query, productId]);

  const selectedItems = visible.filter(item => checked.has(item.id));
  const selectable = aba !== "inativos";
  const allChecked = visible.length > 0 && selectedItems.length === visible.length;

  function toggle(id: string, on: boolean) {
    setChecked(current => { const next = new Set(current); if (on) next.add(id); else next.delete(id); return next; });
  }

  function replaceMany(updated: Affiliation[]) {
    const byId = new Map(updated.map(item => [item.id, item]));
    setAffiliations(current => current.map(item => byId.get(item.id) ?? item));
    setChecked(new Set());
  }

  async function openApproval(targets: Affiliation[]) {
    setApproval(targets);
    setMenuOpen(false);
    setRecurrence("FIRST_CHARGE");
    setCommissionError(undefined);
    setError(null);
    // Sugere a comissão configurada no programa do produto (quando há um só produto na seleção).
    const ids = new Set(targets.map(item => item.productId));
    if (ids.size === 1) {
      try {
        const program = await getAffiliateProgram([...ids][0]!);
        setCommission(String(program.commissionBps / 100).replace(".", ","));
        setRecurrence(program.recurrence);
      } catch { /* mantém o padrão */ }
    }
  }

  async function confirmApproval() {
    if (!approval || submitting) return;
    const bps = parseCommissionPercent(commission);
    if (bps === null) {
      setCommissionError("Informe um percentual entre 0 e 50, com até duas casas decimais.");
      return;
    }
    setSubmitting(true);
    setCommissionError(undefined);
    const done: Affiliation[] = [];
    let failed = 0;
    for (const item of approval) {
      try { done.push(await approveAffiliation(item.id, bps, recurrence)); } catch (requestError) { failed += 1; setError(actionError(requestError)); }
    }
    replaceMany(done);
    setApproval(null);
    setSubmitting(false);
    if (done.length) setSuccess(`${done.length === 1 ? "Afiliação aprovada" : `${done.length} afiliações aprovadas`}. A comissão foi fixada e não poderá ser alterada.${failed ? ` ${failed} não puderam ser aprovadas.` : ""}`);
  }

  async function confirmEnd() {
    if (!endAction || submitting) return;
    setSubmitting(true);
    const done: Affiliation[] = [];
    let failed = 0;
    for (const item of endAction.targets) {
      try { done.push(await endAffiliation(item.id, endAction.reason)); } catch (requestError) { failed += 1; setError(actionError(requestError)); }
    }
    replaceMany(done);
    const rejection = endAction.rejection;
    const reason = endAction.reason;
    setEndAction(null);
    setSubmitting(false);
    if (done.length) setSuccess(`${rejection ? (done.length === 1 ? "Solicitação recusada." : `${done.length} solicitações recusadas.`) : reason === "FRAUD" ? "Vínculo encerrado por fraude." : done.length === 1 ? "Vínculo encerrado." : `${done.length} vínculos encerrados.`}${failed ? ` ${failed} falharam.` : ""}`);
  }

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listSellerAffiliations(nextCursor);
      setAffiliations(current => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais afiliados.");
    } finally {
      setLoadingMore(false);
    }
  }

  return <div className="af">
    <header className="prod-head"><h1>Meus afiliados</h1></header>

    <div className="pe-tabs" role="tablist" aria-label="Situação dos afiliados">
      {abas.map(([id, label]) => <button key={id} type="button" role="tab" id={`af-aba-${id}`} aria-selected={aba === id} aria-controls="af-painel" onClick={() => setAba(id)}>{label}{counts[id] > 0 ? ` (${counts[id]})` : ""}</button>)}
    </div>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => { setError(null); void load(); }}>Recarregar</button></Toast>}
    {success && <Toast>{success} <button className="toast-action" onClick={() => setSuccess(null)}>Fechar</button></Toast>}

    <section id="af-painel" role="tabpanel" aria-labelledby={`af-aba-${aba}`} className="prod-panel">
      <div className="prod-toolbar">
        <label className="prod-search"><span className="sr-only">Buscar por afiliado ou produto</span>
          <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar…" /></label>
        <label className="prod-status af-product"><span className="sr-only">Produto</span>
          <select value={productId} onChange={event => setProductId(event.target.value)}>
            <option value="">Todos os produtos</option>
            {products.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
          </select></label>
        {selectable && <div className="af-actions">
          <button type="button" className="ui-button ui-button-secondary" disabled={selectedItems.length === 0} aria-expanded={menuOpen} aria-haspopup="menu" onClick={() => setMenuOpen(open => !open)}>Ações{selectedItems.length ? ` (${selectedItems.length})` : ""}</button>
          {menuOpen && selectedItems.length > 0 && <div className="prod-menu-list af-menu" role="menu">
            {aba === "pendentes" ? <>
              <button type="button" role="menuitem" className="af-menu-ok" onClick={() => void openApproval(selectedItems)}>Aceitar</button>
              <button type="button" role="menuitem" onClick={() => { setEndAction({ targets: selectedItems, reason: "BY_SELLER", rejection: true }); setMenuOpen(false); }}>Recusar</button>
            </> : <>
              <button type="button" role="menuitem" onClick={() => { setEndAction({ targets: selectedItems, reason: "BY_SELLER", rejection: false }); setMenuOpen(false); }}>Encerrar vínculo</button>
              <button type="button" role="menuitem" onClick={() => { setEndAction({ targets: selectedItems, reason: "FRAUD", rejection: false }); setMenuOpen(false); }}>Informar fraude</button>
            </>}
          </div>}
        </div>}
      </div>

      {loading ? <Skeleton label="Carregando lista de afiliados" /> : visible.length === 0 ?
        <EmptyState title={affiliations.length === 0 ? "Nenhum afiliado ainda" : "Nada por aqui"} description={affiliations.length === 0 ? "Quando alguém pedir afiliação a um produto seu, aparece aqui." : emptyText[aba]} /> :
        <table className="prod-table">
          <thead><tr>
            {selectable && <th scope="col" className="af-check"><input type="checkbox" aria-label="Selecionar todos" checked={allChecked} onChange={event => setChecked(event.target.checked ? new Set(visible.map(item => item.id)) : new Set())} /></th>}
            <th scope="col">Data</th><th scope="col">Nome</th><th scope="col">Produto</th><th scope="col">Comissão %</th><th scope="col">Status</th>
          </tr></thead>
          <tbody>{visible.map(item => <tr key={item.id}>
            {selectable && <td className="af-check"><input type="checkbox" aria-label={`Selecionar ${item.affiliate}`} checked={checked.has(item.id)} onChange={event => toggle(item.id, event.target.checked)} /></td>}
            <td className="prod-muted">{formatDate(item.createdAt)}</td>
            <td><button type="button" className="pe-linkbtn prod-name" onClick={() => setDetail(item)}>{item.affiliate}</button></td>
            <td className="prod-muted">{item.product}</td>
            <td>{item.status === "PENDING" ? "A definir" : formatCommissionBps(item.commissionBps)}</td>
            <td><span className={`pe-pill ${item.status === "APPROVED" ? "pe-pill-on" : item.status === "FRAUD_ENDED" ? "af-pill-bad" : ""}`}>{statusText(item)}</span></td>
          </tr>)}</tbody>
        </table>}
    </section>
    {nextCursor && !loading && <div className="load-more"><button type="button" className="ui-button ui-button-secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</button></div>}

    <Dialog open={Boolean(detail)} title="Detalhes da afiliação" onClose={() => setDetail(null)}>
      {detail && <dl className="detail-list affiliate-detail"><div><dt>Produto</dt><dd>{detail.product}</dd></div><div><dt>Afiliado</dt><dd>{detail.affiliate}</dd></div><div><dt>Situação</dt><dd>{statusText(detail)}</dd></div><div><dt>Comissão</dt><dd>{detail.status === "PENDING" ? "Aguardando definição" : formatCommissionBps(detail.commissionBps)}</dd></div><div><dt>Recorrência</dt><dd>{detail.status === "PENDING" ? "Aguardando definição" : recurrenceLabel[detail.recurrence]}</dd></div><div><dt>Solicitação</dt><dd>{formatDate(detail.createdAt)}</dd></div><div><dt>Aprovação</dt><dd>{formatDate(detail.approvedAt)}</dd></div><div><dt>Encerramento</dt><dd>{formatDate(detail.endedAt)}</dd></div>{detail.endedReason && <div><dt>Motivo</dt><dd>{endReasonLabel[detail.endedReason]}</dd></div>}</dl>}
    </Dialog>

    <Dialog open={Boolean(approval)} title={approval && approval.length > 1 ? `Aceitar ${approval.length} afiliações` : "Aceitar afiliação"} onClose={() => !submitting && setApproval(null)}>
      <p>Defina a comissão com atenção. Depois de aceitar, o percentual e a recorrência ficam fixos neste vínculo.</p>
      <label className="pe-field"><span>Comissão (%)</span><input value={commission} inputMode="decimal" placeholder="Ex.: 15,5" aria-invalid={Boolean(commissionError)} onChange={event => setCommission(event.target.value)} />{commissionError && <small className="pe-error">{commissionError}</small>}</label>
      <label className="pe-field"><span>Recorrência</span><select value={recurrence} onChange={event => setRecurrence(event.target.value as AffiliationRecurrence)}><option value="FIRST_CHARGE">Somente na primeira cobrança</option><option value="ALL_CYCLES">Em todos os ciclos</option></select></label>
      <div className="ui-actions"><button type="button" className="ui-button ui-button-primary" disabled={submitting} onClick={() => void confirmApproval()}>{submitting ? "Aceitando…" : "Confirmar"}</button></div>
    </Dialog>

    <Dialog open={Boolean(endAction)} title={endAction?.rejection ? "Recusar solicitação" : endAction?.reason === "FRAUD" ? "Encerrar por fraude" : "Encerrar vínculo"} onClose={() => !submitting && setEndAction(null)}>
      <p>{endAction?.rejection ? "A solicitação será encerrada sem criar um vínculo." : endAction?.reason === "FRAUD" ? "O vínculo será encerrado e registrado como fraude no histórico. Use somente quando houver evidência." : "O vínculo será encerrado e ficará no histórico somente para consulta."}</p>
      <div className="ui-actions"><button type="button" className="ui-button ui-button-danger" disabled={submitting} onClick={() => void confirmEnd()}>{submitting ? "Processando…" : "Confirmar"}</button></div>
    </Dialog>
  </div>;
}
