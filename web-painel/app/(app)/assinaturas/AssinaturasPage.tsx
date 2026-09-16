"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Botao, Cartao, EmptyState, Etiqueta, Select, Skeleton, Tabela, Toast } from "../../../components/ui";
import {
  isTrialWithoutCard,
  listSubscriptions,
  Subscription,
  SubscriptionStatus,
  subscriptionStatusLabel,
} from "../../../lib/assinaturas";

const statusTone: Record<SubscriptionStatus, "neutral" | "success" | "warning" | "danger"> = {
  TRIAL: "neutral",
  ACTIVE: "success",
  PAST_DUE: "warning",
  CANCELED: "danger",
};

function formatDate(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(iso));
  } catch {
    return iso;
  }
}

export function AssinaturasPage() {
  const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<"" | SubscriptionStatus>("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listSubscriptions();
      setSubscriptions(page.items);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar as assinaturas. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const loadMore = async () => {
    if (!nextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const page = await listSubscriptions(nextCursor);
      setSubscriptions((prev) => [...prev, ...page.items]);
      setNextCursor(page.nextCursor);
    } catch {
      setError("Não foi possível carregar mais assinaturas.");
    } finally {
      setLoadingMore(false);
    }
  };

  const filtered = subscriptions.filter((s) => !statusFilter || s.status === statusFilter);

  const rows = filtered.map((s) => [
    <Link key={s.id} href={`/assinaturas/${s.id}`} className="font-semibold text-primary">
      {s.id.slice(0, 8)}…
    </Link>,
    <Etiqueta key="status" tone={statusTone[s.status]}>
      {subscriptionStatusLabel[s.status]}
      {isTrialWithoutCard(s) && " (sem cartão)"}
    </Etiqueta>,
    <span key="cycle">{s.cycleNumber === 0 ? "Teste" : `Ciclo ${s.cycleNumber}`}</span>,
    <span key="next">
      {s.status === "CANCELED" ? "—" : formatDate(s.nextChargeAt)}
    </span>,
    <span key="cancel">
      {s.cancelPending ? (
        <Etiqueta tone="warning">Cancelamento agendado</Etiqueta>
      ) : (
        "—"
      )}
    </span>,
    <span key="created">{formatDate(s.createdAt)}</span>,
  ]);

  return (
    <div className="assinaturas-page">
      <header className="content-header">
        <div>
          <span className="paysi-rotulo">Operação</span>
          <h1>Assinaturas</h1>
          <p>Acompanhe ciclos, inadimplência e cancelamentos das suas assinaturas.</p>
        </div>
      </header>

      {error && (
        <Toast tone="danger">
          {error}{" "}
          <button className="toast-action" onClick={() => void loadData()}>
            Tentar novamente
          </button>
        </Toast>
      )}

      <Cartao className="assinaturas-filtros" aria-label="Filtros de assinaturas">
        <Select
          label="Status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as "" | SubscriptionStatus)}
        >
          <option value="">Todos os status</option>
          {Object.entries(subscriptionStatusLabel).map(([val, label]) => (
            <option key={val} value={val}>
              {label}
            </option>
          ))}
        </Select>
      </Cartao>

      {loading ? (
        <Skeleton label="Carregando assinaturas" />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="Nenhuma assinatura encontrada"
          description={
            statusFilter
              ? "Nenhum resultado corresponde ao filtro selecionado."
              : "Suas assinaturas aparecerão aqui assim que a primeira for criada."
          }
          action={
            statusFilter ? (
              <Botao variant="secondary" onClick={() => setStatusFilter("")}>
                Limpar filtro
              </Botao>
            ) : undefined
          }
        />
      ) : (
        <>
          <Tabela
            caption="Lista de assinaturas"
            headers={["Assinatura", "Status", "Ciclo", "Próxima cobrança", "Cancelamento", "Criada em"]}
            rows={rows}
          />

          {nextCursor && (
            <div style={{ marginTop: "1.5rem", textAlign: "center" }}>
              <Botao variant="secondary" disabled={loadingMore} onClick={() => void loadMore()}>
                {loadingMore ? "Carregando..." : "Carregar mais assinaturas"}
              </Botao>
            </div>
          )}
        </>
      )}
    </div>
  );
}
