"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Botao, Campo, Dialog, EmptyState, Etiqueta, Select, Skeleton, Toast } from "../../../components/ui";
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
  listSellerAffiliations,
  parseCommissionPercent,
  recurrenceLabel,
} from "../../../lib/afiliados";

type EndAction = { affiliation: Affiliation; reason: "BY_SELLER" | "FRAUD"; rejection: boolean };

function statusTone(status: AffiliationStatus): "neutral" | "success" | "warning" | "danger" {
  if (status === "APPROVED") return "success";
  if (status === "PENDING") return "warning";
  if (status === "FRAUD_ENDED") return "danger";
  return "neutral";
}

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}

function actionError(error: unknown) {
  if (error instanceof ApiRequestError && (error.status === 403 || error.status === 404)) {
    return "A ação não está disponível para esta conta ou o vínculo já mudou.";
  }
  if (error instanceof ApiRequestError && error.status === 409) return "O vínculo já foi atualizado. Recarregue a lista.";
  return "Não foi possível concluir a ação. Tente novamente.";
}

export function AfiliadosPage() {
  const [affiliations, setAffiliations] = useState<Affiliation[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState<"" | AffiliationStatus>("");
  const [selected, setSelected] = useState<Affiliation | null>(null);
  const [approval, setApproval] = useState<Affiliation | null>(null);
  const [commission, setCommission] = useState("");
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

  const visible = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase("pt-BR");
    return affiliations.filter(item => (!status || item.status === status)
      && (!normalized || item.product.toLocaleLowerCase("pt-BR").includes(normalized)
        || item.affiliate.toLocaleLowerCase("pt-BR").includes(normalized)));
  }, [affiliations, query, status]);

  function replaceAffiliation(updated: Affiliation) {
    setAffiliations(current => current.map(item => item.id === updated.id ? updated : item));
    setSelected(current => current?.id === updated.id ? updated : current);
  }

  function openApproval(affiliation: Affiliation) {
    setApproval(affiliation);
    setCommission("");
    setRecurrence("FIRST_CHARGE");
    setCommissionError(undefined);
    setError(null);
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
    try {
      const updated = await approveAffiliation(approval.id, bps, recurrence);
      replaceAffiliation(updated);
      setApproval(null);
      setSuccess("Afiliação aprovada. A comissão foi fixada e não poderá ser alterada.");
    } catch (requestError) {
      setError(actionError(requestError));
      setApproval(null);
    } finally {
      setSubmitting(false);
    }
  }

  async function confirmEnd() {
    if (!endAction || submitting) return;
    setSubmitting(true);
    try {
      const updated = await endAffiliation(endAction.affiliation.id, endAction.reason);
      replaceAffiliation(updated);
      setSuccess(endAction.rejection ? "Solicitação rejeitada." : endAction.reason === "FRAUD" ? "Vínculo encerrado por fraude." : "Vínculo encerrado.");
      setEndAction(null);
    } catch (requestError) {
      setError(actionError(requestError));
      setEndAction(null);
    } finally {
      setSubmitting(false);
    }
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

  return <>
    <header className="content-header affiliate-heading">
      <div><span className="paysi-rotulo">Programa de afiliação</span><h1>Afiliados</h1><p>Analise solicitações, fixe a comissão e acompanhe o histórico dos vínculos.</p></div>
    </header>

    <section className="ui-card affiliate-filters" aria-labelledby="affiliate-filter-title">
      <h2 id="affiliate-filter-title">Filtros</h2>
      <Campo label="Buscar por produto ou afiliado" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Ex.: Curso ou nome" />
      <Select label="Status" value={status} onChange={event => setStatus(event.target.value as "" | AffiliationStatus)}>
        <option value="">Todos</option>
        {Object.entries(affiliationStatusLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </Select>
      {(query || status) && <Botao variant="secondary" onClick={() => { setQuery(""); setStatus(""); }}>Limpar filtros</Botao>}
    </section>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => { setError(null); void load(); }}>Tentar novamente</button></Toast>}
    {success && <Toast>{success} <button className="toast-action" onClick={() => setSuccess(null)}>Fechar</button></Toast>}

    {loading ? <Skeleton label="Carregando lista de afiliados" /> : affiliations.length === 0 ?
      <EmptyState title="Nenhuma solicitação de afiliação" description="Novos pedidos aparecerão aqui para sua análise." /> :
      visible.length === 0 ? <EmptyState title="Nenhum resultado" description="Ajuste os filtros para localizar outro vínculo." action={<Botao variant="secondary" onClick={() => { setQuery(""); setStatus(""); }}>Limpar filtros</Botao>} /> :
      <section className="affiliate-list" aria-label="Lista de afiliados">
        {visible.map(item => <article className="ui-card affiliate-row" key={item.id}>
          <div className="affiliate-main"><div className="ui-labels"><Etiqueta tone={statusTone(item.status)}>{affiliationStatusLabel[item.status]}</Etiqueta><span>{item.status === "PENDING" ? "Termos a definir" : recurrenceLabel[item.recurrence]}</span></div><h2>{item.product}</h2><p>Solicitante: <strong>{item.affiliate}</strong></p></div>
          <dl className="affiliate-summary"><div><dt>Comissão</dt><dd className="paysi-valor">{item.status === "PENDING" ? "A definir" : formatCommissionBps(item.commissionBps)}</dd></div><div><dt>Solicitado em</dt><dd>{formatDate(item.createdAt)}</dd></div></dl>
          <div className="affiliate-actions">
            <Botao variant="secondary" onClick={() => setSelected(item)}>Ver detalhes</Botao>
            {item.status === "PENDING" && <><Botao onClick={() => openApproval(item)}>Aprovar</Botao><Botao variant="danger" onClick={() => setEndAction({ affiliation: item, reason: "BY_SELLER", rejection: true })}>Rejeitar</Botao></>}
            {item.status === "APPROVED" && <><Botao variant="secondary" onClick={() => setEndAction({ affiliation: item, reason: "BY_SELLER", rejection: false })}>Encerrar</Botao><Botao variant="danger" onClick={() => setEndAction({ affiliation: item, reason: "FRAUD", rejection: false })}>Informar fraude</Botao></>}
          </div>
        </article>)}
      </section>}
    {nextCursor && !loading && <div className="load-more"><Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</Botao></div>}

    <Dialog open={Boolean(selected)} title="Detalhes da afiliação" onClose={() => setSelected(null)}>
      {selected && <><div className="ui-labels"><Etiqueta tone={statusTone(selected.status)}>{affiliationStatusLabel[selected.status]}</Etiqueta></div><dl className="detail-list affiliate-detail"><div><dt>Produto</dt><dd>{selected.product}</dd></div><div><dt>Afiliado</dt><dd>{selected.affiliate}</dd></div><div><dt>Comissão</dt><dd>{selected.status === "PENDING" ? "Aguardando definição" : formatCommissionBps(selected.commissionBps)}</dd></div><div><dt>Recorrência</dt><dd>{selected.status === "PENDING" ? "Aguardando definição" : recurrenceLabel[selected.recurrence]}</dd></div><div><dt>Solicitação</dt><dd>{formatDate(selected.createdAt)}</dd></div><div><dt>Aprovação</dt><dd>{formatDate(selected.approvedAt)}</dd></div><div><dt>Encerramento</dt><dd>{formatDate(selected.endedAt)}</dd></div>{selected.endedReason && <div><dt>Motivo</dt><dd>{endReasonLabel[selected.endedReason]}</dd></div>}</dl>{selected.status === "APPROVED" && <p className="contract-lock">Comissão bloqueada após a aprovação. O histórico é somente leitura.</p>}</>}
    </Dialog>

    <Dialog open={Boolean(approval)} title="Aprovar afiliação" onClose={() => !submitting && setApproval(null)}>
      <p>Defina a comissão com atenção. Após aprovar, o percentual e a recorrência ficam bloqueados neste vínculo.</p>
      <Campo label="Comissão (%)" value={commission} inputMode="decimal" placeholder="Ex.: 15,5" error={commissionError} onChange={event => setCommission(event.target.value)} />
      <Select label="Recorrência" value={recurrence} onChange={event => setRecurrence(event.target.value as AffiliationRecurrence)}><option value="FIRST_CHARGE">Somente na primeira cobrança</option><option value="ALL_CYCLES">Em todos os ciclos</option></Select>
      <div className="ui-actions"><Botao disabled={submitting} onClick={() => void confirmApproval()}>{submitting ? "Aprovando…" : "Confirmar aprovação"}</Botao></div>
    </Dialog>

    <Dialog open={Boolean(endAction)} title={endAction?.rejection ? "Rejeitar solicitação" : endAction?.reason === "FRAUD" ? "Encerrar por fraude" : "Encerrar vínculo"} onClose={() => !submitting && setEndAction(null)}>
      <p>{endAction?.rejection ? "A solicitação será encerrada sem criar um vínculo aprovado." : endAction?.reason === "FRAUD" ? "O vínculo será encerrado e ficará registrado como fraude no histórico. Use esta opção somente quando houver evidência." : "O vínculo será encerrado normalmente e permanecerá disponível no histórico somente para consulta."}</p>
      <div className="ui-actions"><Botao variant="danger" disabled={submitting} onClick={() => void confirmEnd()}>{submitting ? "Processando…" : endAction?.rejection ? "Confirmar rejeição" : "Confirmar encerramento"}</Botao></div>
    </Dialog>
  </>;
}
