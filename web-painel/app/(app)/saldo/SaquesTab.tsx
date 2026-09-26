"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../components/ui";
import { Paginacao } from "../../../components/Paginacao";
import { formatarCentavos } from "../../../lib/moeda";
import { formatPixKey, listPayouts, PayoutRow, PayoutsPage, payoutStatusLabel, payoutStatusTone } from "../../../lib/financeiro";

const dateOnly = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" });
const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });

function pill(status: PayoutRow["status"]) {
  const tone = payoutStatusTone[status];
  return `pe-pill ${tone === "ok" ? "pe-pill-on" : tone === "bad" ? "af-pill-bad" : "vd-pill-warn"}`;
}

function Linha({ rotulo, children }: { rotulo: string; children: React.ReactNode }) {
  return <div><dt>{rotulo}</dt><dd>{children}</dd></div>;
}

function SaqueDrawer({ payout, onClose }: { payout: PayoutRow | null; onClose: () => void }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (payout && !dialog.open) dialog.showModal();
    if (!payout && dialog.open) dialog.close();
  }, [payout]);
  return <dialog ref={ref} className="mk-drawer" aria-labelledby={titleId} onCancel={event => { event.preventDefault(); onClose(); }}>
    <header className="vd-head"><h2 id={titleId}>Ver detalhes</h2><button type="button" className="cp-close" aria-label="Fechar" onClick={onClose}>✕</button></header>
    {payout && <div className="mk-drawer-body"><dl className="mk-facts">
      <Linha rotulo="Valor">{formatarCentavos(payout.amountCents)}</Linha>
      <Linha rotulo="Data">{dateTime.format(new Date(payout.createdAt))}</Linha>
      <Linha rotulo="Destino">{payout.destinationName}</Linha>
      <Linha rotulo="Chave PIX">{formatPixKey(payout.pixKey, payout.pixKeyType)}</Linha>
      <Linha rotulo="Status"><span className={pill(payout.status)}>{payoutStatusLabel[payout.status]}</span></Linha>
      {payout.receiptUrl && <Linha rotulo="Comprovante"><a href={payout.receiptUrl} target="_blank" rel="noopener noreferrer">Abrir comprovante</a></Linha>}
    </dl></div>}
  </dialog>;
}

/** Aba Saques: histórico com data, valor e status; clicar abre o detalhe do saque. `reloadKey` recarrega após um saque novo. */
export function SaquesTab({ reloadKey }: { reloadKey: number }) {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PayoutsPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<PayoutRow | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listPayouts(page);
      if (id === requestId.current) setData(result);
    } catch {
      if (id === requestId.current) setError("Não foi possível carregar os saques. Tente novamente.");
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [page]);

  useEffect(() => { void load(); }, [load, reloadKey]);

  const rows = data?.items ?? [];
  return <section className="prod-panel" aria-label="Histórico de saques">
    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading && !data ? <Skeleton label="Carregando saques" /> : rows.length === 0 ?
      <EmptyState title="Nenhum saque ainda" description="Quando você sacar o saldo disponível, o histórico aparece aqui." /> :
      <div className="dash-table-wrap"><table className="prod-table vd-table" aria-busy={loading}>
        <thead><tr><th scope="col">Data</th><th scope="col">Valor</th><th scope="col">Status</th></tr></thead>
        <tbody>{rows.map(row => <tr key={row.id} className="vd-row" onClick={() => setSelected(row)}>
          <td className="prod-muted"><button type="button" className="pe-linkbtn vd-buyer" aria-label={`Ver detalhes do saque de ${dateOnly.format(new Date(row.createdAt))}`} onClick={event => { event.stopPropagation(); setSelected(row); }}>{dateOnly.format(new Date(row.createdAt))}</button></td>
          <td><strong>{formatarCentavos(row.amountCents)}</strong></td>
          <td><span className={pill(row.status)}>{payoutStatusLabel[row.status]}</span></td>
        </tr>)}</tbody>
      </table></div>}
    {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    <SaqueDrawer payout={selected} onClose={() => setSelected(null)} />
  </section>;
}
