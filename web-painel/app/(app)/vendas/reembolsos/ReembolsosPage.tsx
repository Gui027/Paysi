"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { EmptyState, Skeleton, Toast } from "../../../../components/ui";
import { Paginacao } from "../../../../components/Paginacao";
import { formatarCentavos } from "../../../../lib/moeda";
import { listRefunds, refundOriginLabel, RefundRow, RefundsPage, refundStatusLabel } from "../../../../lib/vendas";

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const abas: readonly ["all" | RefundRow["status"], string][] = [["all", "Todos"], ["SUCCEEDED", "Concluídos"], ["PENDING", "Em processamento"], ["FAILED", "Falharam"]];

export function ReembolsosPage() {
  const [aba, setAba] = useState<"all" | RefundRow["status"]>("all");
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(1);
  const [data, setData] = useState<RefundsPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const requestId = useRef(0);

  const load = useCallback(async () => {
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    try {
      const result = await listRefunds(query, aba === "all" ? [] : [aba], page);
      if (id === requestId.current) setData(result);
    } catch {
      if (id === requestId.current) setError("Não foi possível carregar os reembolsos. Tente novamente.");
    } finally {
      if (id === requestId.current) setLoading(false);
    }
  }, [query, aba, page]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    const timer = window.setTimeout(() => { setQuery(search); setPage(1); }, 350);
    return () => window.clearTimeout(timer);
  }, [search]);

  const rows = data?.items ?? [];

  return <div className="vd">
    <header className="prod-head"><h1>Reembolsos</h1></header>

    <div className="pe-tabs" role="tablist" aria-label="Situação dos reembolsos">
      {abas.map(([id, label]) => <button key={id} type="button" role="tab" id={`rb-aba-${id}`} aria-selected={aba === id} aria-controls="rb-lista" onClick={() => { setAba(id); setPage(1); }}>{label}</button>)}
    </div>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}

    <section id="rb-lista" role="tabpanel" aria-labelledby={`rb-aba-${aba}`} className="prod-panel">
      <div className="prod-toolbar">
        <label className="prod-search"><span className="sr-only">Buscar reembolsos</span>
          <input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Buscar…" /></label>
      </div>
      {loading && !data ? <Skeleton label="Carregando reembolsos" /> : rows.length === 0 ?
        <EmptyState title="Nenhum reembolso" description="Quando uma venda for reembolsada, ela aparece aqui. Para reembolsar, abra a venda em Vendas e use “Reembolsar venda”." /> :
        <div className="dash-table-wrap"><table className="prod-table">
          <thead><tr><th scope="col">Data</th><th scope="col">Venda</th><th scope="col">Produto</th><th scope="col">Cliente</th><th scope="col">Valor</th><th scope="col">Solicitado por</th><th scope="col">Status</th></tr></thead>
          <tbody>{rows.map(row => <tr key={row.id}>
            <td className="prod-muted">{dateTime.format(new Date(row.createdAt))}</td>
            <td className="prod-muted">{row.saleCode}</td>
            <td className="prod-muted">{row.productName}</td>
            <td><span className="vd-buyer"><strong>{row.buyerName}</strong><small>{row.buyerEmail}</small></span>{row.reason && <small className="vd-offer">Motivo: {row.reason}</small>}</td>
            <td>{formatarCentavos(row.amountCents)}</td>
            <td className="prod-muted">{refundOriginLabel[row.requestedBy]}</td>
            <td><span className={`pe-pill ${row.status === "SUCCEEDED" ? "pe-pill-on" : row.status === "FAILED" ? "af-pill-bad" : "vd-pill-warn"}`}>{refundStatusLabel[row.status]}</span></td>
          </tr>)}</tbody>
        </table></div>}
      {data && <Paginacao page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </section>
  </div>;
}
