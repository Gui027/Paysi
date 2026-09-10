"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Botao, Cartao, Dialog, EmptyState, Etiqueta, Select, Skeleton, Toast } from "../../../components/ui";
import { BalanceView, Bucket, Direction, LedgerItem, getBalance, getLedgerEntries } from "../../../lib/dashboard";
import { formatarCentavos } from "../../../lib/moeda";

const buckets = ["GUARANTEE", "PENDING", "RESERVE", "AVAILABLE", "DEBT"] as const;

const bucketLabel: Record<(typeof buckets)[number], string> = {
  GUARANTEE: "Em garantia",
  PENDING: "Pendente",
  RESERVE: "Reserva",
  AVAILABLE: "Disponível",
  DEBT: "Dívida",
};

const bucketHelp: Record<(typeof buckets)[number], string> = {
  GUARANTEE: "Valores retidos até o fim da garantia.",
  PENDING: "Valores aguardando a data de liberação.",
  RESERVE: "Reserva de segurança para riscos da operação.",
  AVAILABLE: "Valor liberado para movimentação.",
  DEBT: "Obrigações a compensar com entradas futuras.",
};

const originLabel: Record<string, string> = {
  SALE: "Venda",
  COMMISSION: "Comissão",
  FEE: "Taxa",
  DEBT: "Compensação de dívida",
  OTHER: "Outro ajuste",
};

const directionLabel: Record<Direction, string> = { CREDIT: "Crédito", DEBIT: "Débito" };

function formatDate(value: string | null) {
  return value ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(value)) : "—";
}

function balanceFor(view: BalanceView, bucket: (typeof buckets)[number]) {
  const field = bucket.toLocaleLowerCase("en-US") as "guarantee" | "pending" | "reserve" | "available" | "debt";
  return view[field];
}

function memoryExplanation(item: LedgerItem) {
  if (item.origin === "FEE") return "Taxa registrada pela plataforma. Quando aplicável, a descrição identifica custos como a verificação de identidade (KYC).";
  if (item.origin === "DEBT" || item.bucket === "DEBT") return "Este lançamento registra a origem ou a compensação de uma dívida. Entradas futuras podem reduzir o saldo devedor conforme as regras do razão.";
  if (item.origin === "COMMISSION") return "Comissão registrada a partir da memória financeira da cobrança vinculada.";
  if (item.origin === "SALE") return "Valor originado por uma cobrança confirmada e distribuído conforme a memória financeira da venda.";
  return "Ajuste identificado pelo motivo e pela referência informados abaixo.";
}

export function SaldoPage() {
  const [balance, setBalance] = useState<BalanceView | null>(null);
  const [entries, setEntries] = useState<LedgerItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bucket, setBucket] = useState<"" | Bucket>("");
  const [origin, setOrigin] = useState("");
  const [selected, setSelected] = useState<LedgerItem | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextBalance, page] = await Promise.all([getBalance(), getLedgerEntries()]);
      setBalance(nextBalance);
      setEntries(page.items);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar o saldo e o extrato. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const visibleEntries = useMemo(() => entries.filter(item => (!bucket || item.bucket === bucket) && (!origin || item.origin === origin)), [entries, bucket, origin]);
  const debtEntries = entries.filter(item => item.bucket === "DEBT" || item.origin === "DEBT");

  async function loadMore() {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await getLedgerEntries(nextCursor);
      setEntries(current => [...current, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais lançamentos.");
    } finally {
      setLoadingMore(false);
    }
  }

  return <>
    <header className="content-header balance-heading"><div><span className="paysi-rotulo">Financeiro</span><h1>Saldo e extrato</h1><p>Entenda onde cada valor está e consulte a origem de todos os lançamentos.</p></div><Botao variant="secondary" disabled={loading} onClick={() => void load()}>Atualizar</Botao></header>

    {error && <Toast tone="danger">{error} <button className="toast-action" onClick={() => void load()}>Tentar novamente</button></Toast>}
    {loading || !balance ? <Skeleton label="Carregando saldo e extrato" /> : <>
      <p className="balance-as-of">Saldo efetivo em <strong>{formatDate(balance.asOf)}</strong></p>
      <section className="balance-grid" aria-label="Saldo por categoria">
        {buckets.map(item => <Cartao className={`balance-card balance-card-${item.toLocaleLowerCase("en-US")}`} key={item}><span>{bucketLabel[item]}</span><strong className="paysi-valor">{formatarCentavos(balanceFor(balance, item))}</strong><small>{bucketHelp[item]}</small></Cartao>)}
      </section>

      {balance.debt !== 0 && <section className="debt-warning" role="alert"><div><h2>Há saldo devedor</h2><p>Consulte abaixo os lançamentos que originaram a dívida. Entradas futuras podem ser usadas na compensação.</p></div><strong className="paysi-valor">{formatarCentavos(balance.debt)}</strong>{debtEntries.length > 0 && <ul>{debtEntries.slice(0, 3).map(item => <li key={item.entryId}><button onClick={() => setSelected(item)}>{item.reason || originLabel[item.origin] || item.origin} — {formatarCentavos(item.amountCents)}</button></li>)}</ul>}</section>}

      <section className="ledger-section" aria-labelledby="ledger-title">
        <div className="section-heading"><div><h2 id="ledger-title">Extrato</h2><p>Valores são exibidos exatamente como recebidos da API.</p></div><div className="ledger-filters"><Select label="Categoria" value={bucket} onChange={event => setBucket(event.target.value as "" | Bucket)}><option value="">Todas</option>{buckets.map(item => <option key={item} value={item}>{bucketLabel[item]}</option>)}</Select><Select label="Origem" value={origin} onChange={event => setOrigin(event.target.value)}><option value="">Todas</option>{Object.entries(originLabel).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</Select></div></div>
        {entries.length === 0 ? <EmptyState title="Nenhum lançamento" description="Os movimentos financeiros da conta aparecerão aqui." /> : visibleEntries.length === 0 ? <EmptyState title="Nenhum resultado" description="Não há lançamentos com os filtros selecionados." action={<Botao variant="secondary" onClick={() => { setBucket(""); setOrigin(""); }}>Limpar filtros</Botao>} /> : <div className="ui-card ledger-table-wrap"><table className="ui-table ledger-table"><caption>Histórico de lançamentos</caption><thead><tr><th scope="col">Data</th><th scope="col">Origem</th><th scope="col">Categoria</th><th scope="col">Movimento</th><th scope="col">Valor</th><th scope="col">Disponível em</th><th scope="col">Memória</th></tr></thead><tbody>{visibleEntries.map(item => <tr key={item.entryId}><td>{formatDate(item.createdAt)}</td><td>{originLabel[item.origin] || item.origin}</td><td>{bucketLabel[item.bucket as keyof typeof bucketLabel] || item.bucket}</td><td><Etiqueta tone={item.direction === "CREDIT" ? "success" : "warning"}>{directionLabel[item.direction]}</Etiqueta></td><td className="paysi-valor">{formatarCentavos(item.amountCents)}</td><td>{formatDate(item.availableAt)}</td><td><button className="ledger-detail-button" onClick={() => setSelected(item)}>Ver origem</button></td></tr>)}</tbody></table></div>}
        {nextCursor && <div className="load-more"><Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? "Carregando…" : "Carregar mais"}</Botao></div>}
      </section>
    </>}

    <Dialog open={Boolean(selected)} title="Memória do lançamento" onClose={() => setSelected(null)}>
      {selected && <><p>{memoryExplanation(selected)}</p><dl className="detail-list ledger-memory"><div><dt>Origem</dt><dd>{originLabel[selected.origin] || selected.origin}</dd></div><div><dt>Motivo</dt><dd>{selected.reason || "Não informado"}</dd></div><div><dt>Referência</dt><dd><code>{selected.reference || "Não informada"}</code></dd></div><div><dt>Categoria</dt><dd>{bucketLabel[selected.bucket as keyof typeof bucketLabel] || selected.bucket}</dd></div><div><dt>Movimento</dt><dd>{directionLabel[selected.direction]}</dd></div><div><dt>Valor</dt><dd className="paysi-valor">{formatarCentavos(selected.amountCents)}</dd></div><div><dt>Registrado em</dt><dd>{formatDate(selected.createdAt)}</dd></div><div><dt>Liberação prevista</dt><dd>{formatDate(selected.availableAt)}</dd></div></dl></>}
    </Dialog>
  </>;
}
